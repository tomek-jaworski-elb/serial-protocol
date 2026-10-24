package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

/**
 * Puts the database back to what a backup holds (docs/sync-spec.md § Backups): <em>replace</em>
 * mode, the answer to "the last import was a mistake".
 *
 * <p>Its own {@code before-restore} backup is written first, so a restore is itself undoable;
 * the backup directory is pruned only after the restore succeeded, so the file being restored is
 * never deleted from under it. Then, inside one transaction, every synchronised table is emptied in reverse foreign-key
 * order and loaded from the file row by row, the photo bytes and the sequences with them. It
 * never runs the collision analysis: the file is not merged, it wins outright. The node keeps
 * its id ({@code sync_node} is not in the file) and only records the restore as its last
 * import.</p>
 */
@Service
public class SyncRestoreService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncRestoreService.class);

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final SyncBackupService backups;
  private final SyncNodeService nodeService;
  private final SyncExporter exporter;
  private final ThumbnailBackfill thumbnails;
  private final Clock clock;

  public SyncRestoreService(DataSource dataSource, PlatformTransactionManager transactionManager,
      SyncBackupService backups, SyncNodeService nodeService, SyncExporter exporter,
      ThumbnailBackfill thumbnails, Clock clock) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transaction = new TransactionTemplate(transactionManager);
    this.backups = backups;
    this.nodeService = nodeService;
    this.exporter = exporter;
    this.thumbnails = thumbnails;
    this.clock = clock;
  }

  /**
   * @param backup a file from the backup directory ({@link SyncBackupService#resolve})
   * @throws SyncRefusedException when the file is not a backup this program can load
   */
  public SyncRestoreResult restore(Path backup) throws IOException {
    SyncFile file = SyncReader.read(backup);
    check(file);
    Path before = backups.writeBackup("before-restore");
    Instant now = Instant.now(clock);
    String name = backup.getFileName().toString();
    int[] counts = transaction.execute(status -> {
      try (ZipFile zip = new ZipFile(backup.toFile(), StandardCharsets.UTF_8)) {
        return load(zip, file, now, name);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    int rebuilt = SyncHousekeeping.afterCommit(LOG, "restore", backups, thumbnails);
    LOG.info("Restored {}: {} row(s), {} photo(s); the state before it is in {}", name, counts[0], counts[1],
        before.getFileName());
    return new SyncRestoreResult(name, before, counts[0], counts[1], rebuilt);
  }

  /**
   * A restore empties every table before reloading it, so a backup that lacks a table's entry
   * (a truncated or hand-edited file) would silently empty that table; it is refused instead.
   */
  private void check(SyncFile file) {
    SyncManifest manifest = file.manifest();
    if (!manifest.backup()) {
      throw new SyncRefusedException("The file is a sync file from another installation, not a backup; "
          + "load it with Import, which reports the collisions first.");
    }
    if (!Objects.equals(manifest.schemaVersion(), exporter.schemaVersion())) {
      throw new SyncRefusedException("The backup was made with database schema version "
          + String.valueOf(manifest.schemaVersion()) + " and this computer runs version "
          + String.valueOf(exporter.schemaVersion()) + "; it cannot be restored into this program.");
    }
    if (!file.missingTables().isEmpty()) {
      throw new SyncRefusedException("The backup is incomplete: it has no entry for "
          + String.join(", ", file.missingTables()) + "; restoring it would empty those tables.");
    }
  }

  private int[] load(ZipFile zip, SyncFile file, Instant now, String name) {
    SyncJdbcWriter writer = new SyncJdbcWriter(jdbc);
    List<SyncTable> tables = SyncTables.TABLES;
    jdbc.update("delete from " + SyncTables.BLOB_TABLE);
    for (int i = tables.size() - 1; i >= 0; i--) {
      jdbc.update("delete from " + tables.get(i).name());
    }
    int rows = 0;
    for (SyncTable table : tables) {
      for (SyncRow row : file.rows(table.name())) {
        writer.insert(table, row);
        rows++;
      }
    }
    int blobs = 0;
    for (String id : file.blobIds()) {
      writer.insertBlob(zip, id, file.thumbIds().contains(id));
      blobs++;
    }
    nodeService.markRestored(now, name);
    return new int[] {rows, blobs};
  }
}
