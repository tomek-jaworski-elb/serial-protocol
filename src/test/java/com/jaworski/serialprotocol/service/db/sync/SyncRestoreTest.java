package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 5's acceptance test (docs/sync-spec.md § Backups): a backup restored after further
 * edits brings the rows, sequences and photo bytes back exactly, writes its own backup first,
 * keeps the node id and never consults the collision analysis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"sync.backup-dir=target/sync-restore-test", "sync.work-dir=target/sync-restore-work"})
class SyncRestoreTest {

  private static final UUID NEW_PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000021");

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncBackupService backups;
  @Autowired
  private SyncRestoreService restores;
  @Autowired
  private SyncLocalReader localReader;
  @Autowired
  private SyncNodeService nodeService;
  @Autowired
  private SyncExporter exporter;

  /** A successful restore prunes, so the directory must not carry over the previous runs' files. */
  @BeforeEach
  void cleanDirectory() throws IOException {
    FileSystemUtils.deleteRecursively(backups.directory());
  }

  @Test
  void aRestoreBringsBackTheBackedUpStateAndKeepsTheNodeId() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    UUID self = nodeService.current().getId();
    Map<String, List<SyncRow>> before = localReader.read();
    Path backup = backups.writeBackup("manual");

    // Everything a mistaken import could do: an edit, a new numbered record, a deletion, a
    // sequence moved on, a photo replaced.
    jdbc.update("update participants set participants_email = ?, version = 1 where participants_uuid = ?",
        "wrong@example", SyncFixture.PARTICIPANT);
    jdbc.update("insert into participants (participants_uuid, participants_id, participants_name, participants_surname,"
        + " created_at, updated_at, version, origin_node) values (?, ?, ?, ?, ?, ?, 0, ?)", NEW_PARTICIPANT, 1002L,
        "Nobody", "New", SyncFixture.AT, SyncFixture.AT, SyncFixture.ORIGIN);
    jdbc.update("update id_sequence set id_sequence_next = 1003 where id_sequence_name = 'participant'");
    jdbc.update("delete from courses_trainers");
    jdbc.update("update trainer set deleted_at = ? where trainer_uuid = ?", SyncFixture.AT, SyncFixture.TRAINER);
    jdbc.update("update image_blob set image_data = ?, image_thumb_data = null where image_uuid = ?",
        new byte[] {7, 7, 7}, SyncFixture.IMAGE);
    assertThat(localReader.read()).isNotEqualTo(before);
    int backupsBefore = backups.list().size();

    SyncRestoreResult result = restores.restore(backup);

    assertThat(localReader.read()).isEqualTo(before);
    assertThat(jdbc.queryForObject("select image_data from image_blob where image_uuid = ?", byte[].class,
        SyncFixture.IMAGE)).isEqualTo(SyncFixture.PHOTO);
    assertThat(jdbc.queryForObject("select image_thumb_data from image_blob where image_uuid = ?", byte[].class,
        SyncFixture.IMAGE)).isEqualTo(SyncFixture.THUMB);
    assertThat(jdbc.queryForObject("select count(*) from image_blob", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("select id_sequence_next from id_sequence where id_sequence_name = 'participant'",
        Long.class)).isEqualTo(1002L);
    assertThat(nodeService.current().getId()).isEqualTo(self);
    assertThat(nodeService.current().getLastImportFile()).isEqualTo(backup.getFileName().toString());
    assertThat(result.restored()).isEqualTo(backup.getFileName().toString());
    assertThat(result.blobs()).isEqualTo(1);
    assertThat(result.rows()).isEqualTo(before.values().stream().mapToInt(List::size).sum());
    assertThat(result.beforeRestore()).exists();
    assertThat(result.beforeRestore().getFileName().toString()).contains("before-restore");
    assertThat(backups.list()).hasSize(backupsBefore + 1);
    assertThat(Files.exists(SyncBackupService.reportFor(result.beforeRestore()))).isFalse();
  }

  @Test
  void aRestoreForgetsTheBaseOfTheLastImport() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Path backup = backups.writeBackup("manual");
    nodeService.markImported(Instant.now(), "other.sync", SyncFixture.ORIGIN, Instant.now());

    restores.restore(backup);

    Map<String, Object> node = jdbc.queryForMap("select * from sync_node");
    assertThat(node.get("LAST_IMPORT_NODE")).isNull();
    assertThat(node.get("LAST_IMPORT_EXPORTED_AT")).isNull();
    assertThat(node.get("LAST_IMPORT_FILE")).isEqualTo(backup.getFileName().toString());
  }

  @Test
  void theBackupTakenBeforeARestoreUndoesTheRestore() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Path older = backups.writeBackup("manual");
    jdbc.update("update participants set participants_email = ? where participants_uuid = ?",
        "newer@example", SyncFixture.PARTICIPANT);
    Map<String, List<SyncRow>> newer = localReader.read();

    SyncRestoreResult first = restores.restore(older);
    assertThat(localReader.read()).isNotEqualTo(newer);

    restores.restore(first.beforeRestore());
    assertThat(localReader.read()).isEqualTo(newer);
  }

  @Test
  void aBackupWithoutATableEntryIsRefusedInsteadOfEmptyingThatTable() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Map<String, List<SyncRow>> before = localReader.read();
    Path whole = backups.writeBackup("manual");
    Map<String, byte[]> entries = SyncFormatTest.unzip(Files.readAllBytes(whole));
    entries.remove(SyncTables.tableEntry("courses"));
    entries.remove(SyncTables.tableEntry("courses_trainers"));
    Path cut = backups.directory().resolve("cut.bak");
    Files.write(cut, SyncFixture.zip(entries));
    int backupsBefore = backups.list().size();

    assertThatThrownBy(() -> restores.restore(cut))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("incomplete").hasMessageContaining("courses, courses_trainers");

    assertThat(localReader.read()).isEqualTo(before);
    assertThat(backups.list()).hasSize(backupsBefore);
  }

  @Test
  void aSyncFileFromAnotherInstallationIsNotRestoredAndNothingChanges() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Map<String, List<SyncRow>> before = localReader.read();
    Files.createDirectories(backups.directory());
    Path notABackup = backups.directory().resolve("other.bak");
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      exporter.export(out, false);
      Files.write(notABackup, out.toByteArray());
    }
    int backupsBefore = backups.list().size();

    assertThatThrownBy(() -> restores.restore(notABackup))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("not a backup");

    assertThat(localReader.read()).isEqualTo(before);
    assertThat(backups.list()).hasSize(backupsBefore);
  }
}
