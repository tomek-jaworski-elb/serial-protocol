package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code .bak} files in {@code sync.backup-dir}: written before every import and
 * restore and on request, the newest {@code sync.backup-keep} kept (docs/sync-spec.md § Backups).
 *
 * <p>A backup is an ordinary sync file with {@code backup=true} in its manifest, written to a
 * temporary name first and renamed when complete, so a crash mid-way never leaves a half file
 * that looks restorable. Writing never prunes: the caller calls {@link #prune()} once the import
 * or restore the backup was taken for has succeeded, so a refused import does not evict an older
 * backup and a restore never deletes the file it is loading.</p>
 */
@Service
public class SyncBackupService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncBackupService.class);

  /** One file of the backup directory, as the page lists it. */
  public record BackupFile(String name, long size, Instant modified) {
  }

  private final SyncExporter exporter;
  private final SyncNodeService nodeService;
  private final SyncProperties properties;
  private final Clock clock;

  public SyncBackupService(SyncExporter exporter, SyncNodeService nodeService, SyncProperties properties, Clock clock) {
    this.exporter = exporter;
    this.nodeService = nodeService;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Exports the database into a new backup file and prunes the oldest beyond the limit.
   *
   * @param reason {@code before-import}, {@code before-restore} or {@code manual}; part of the name
   * @return the file written
   */
  public Path writeBackup(String reason) throws IOException {
    Path dir = directory();
    Files.createDirectories(dir);
    Path target = uniqueName(dir, reason);
    Path partial = dir.resolve(target.getFileName() + ".part");
    try (OutputStream out = Files.newOutputStream(partial)) {
      exporter.export(out, true);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(partial);
      throw e;
    }
    Files.move(partial, target);
    LOG.info("Backup written: {} ({} bytes)", target.getFileName(), Files.size(target));
    return target;
  }

  /** Newest first. An absent directory is simply empty. */
  public List<BackupFile> list() throws IOException {
    Path dir = directory();
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    List<BackupFile> files = new ArrayList<>();
    try (Stream<Path> paths = Files.list(dir)) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        if (isBackup(path)) {
          files.add(new BackupFile(path.getFileName().toString(), Files.size(path),
              Files.getLastModifiedTime(path).toInstant()));
        }
      }
    }
    files.sort(Comparator.comparing(BackupFile::modified).thenComparing(BackupFile::name).reversed());
    return files;
  }

  /**
   * The backup with this exact file name, or empty: a name with a path separator, another
   * extension or no file behind it is never resolved, so the download endpoint cannot be
   * pointed outside the directory.
   */
  public java.util.Optional<Path> resolve(String name) {
    if (name == null || name.contains("/") || name.contains("\\") || !name.endsWith(SyncFileNames.BACKUP_EXTENSION)) {
      return java.util.Optional.empty();
    }
    Path path = directory().resolve(name).normalize();
    if (!path.getParent().equals(directory()) || !Files.isRegularFile(path)) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(path);
  }

  /** The import report kept next to a backup: {@code <backup>.report.json}. */
  public static Path reportFor(Path backup) {
    return backup.resolveSibling(backup.getFileName() + ".report.json");
  }

  /** Deletes the oldest backups beyond {@code sync.backup-keep}, with their import reports; 0 keeps every file. */
  public int prune() throws IOException {
    int keep = properties.getBackupKeep();
    if (keep <= 0) {
      return 0;
    }
    List<BackupFile> files = list();
    int deleted = 0;
    for (BackupFile old : files.subList(Math.min(keep, files.size()), files.size())) {
      Path path = directory().resolve(old.name());
      Files.deleteIfExists(path);
      Files.deleteIfExists(reportFor(path));
      deleted++;
      LOG.info("Backup pruned: {}", old.name());
    }
    return deleted;
  }

  public Path directory() {
    return properties.getBackupDir().toAbsolutePath().normalize();
  }

  private Path uniqueName(Path dir, String reason) {
    String base = SyncFileNames.backupName(nodeService.current().getName(), Instant.now(clock), clock.getZone(), reason);
    Path candidate = dir.resolve(base);
    for (int n = 2; Files.exists(candidate); n++) {
      String stem = base.substring(0, base.length() - SyncFileNames.BACKUP_EXTENSION.length());
      candidate = dir.resolve(stem + "-" + n + SyncFileNames.BACKUP_EXTENSION);
    }
    return candidate;
  }

  private static boolean isBackup(Path path) {
    try {
      return Files.isRegularFile(path) && path.getFileName().toString().endsWith(SyncFileNames.BACKUP_EXTENSION);
    } catch (UncheckedIOException e) {
      return false;
    }
  }
}
