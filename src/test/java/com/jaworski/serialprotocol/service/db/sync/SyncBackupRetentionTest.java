package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Backups are kept to {@code sync.backup-keep}, the newest surviving, and only {@code .bak} files count. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "sync.backup-dir=target/sync-backup-test",
    "sync.backup-keep=3"})
class SyncBackupRetentionTest {

  @Autowired
  private SyncBackupService backups;

  @BeforeEach
  void cleanDirectory() throws IOException {
    FileSystemUtils.deleteRecursively(backups.directory());
  }

  @Test
  void onlyTheNewestBackupsSurvive() throws IOException {
    Path dir = backups.directory();
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < 5; i++) {
      Path written = backups.writeBackup("manual");
      assertThat(written).exists();
      // Five in one second: the name takes a -2, -3 suffix instead of overwriting the previous file.
      assertThat(written.getFileName().toString()).matches(".+-\\d{8}-\\d{6}-manual(-\\d+)?\\.bak");
      Files.setLastModifiedTime(written, FileTime.from(base.plusSeconds(60L * i)));
      backups.prune();
    }
    Files.writeString(dir.resolve("notes.txt"), "not a backup");

    List<SyncBackupService.BackupFile> kept = backups.list();
    assertThat(kept).hasSize(3);
    assertThat(kept.get(0).modified()).isAfter(kept.get(1).modified());
    assertThat(kept.get(2).modified()).isEqualTo(base.plusSeconds(120));
    assertThat(dir.resolve("notes.txt")).exists();
    try (var files = Files.list(dir)) {
      assertThat(files.filter(p -> p.toString().endsWith(".part"))).isEmpty();
    }
  }

  @Test
  void aBackupIsASyncFileMarkedAsBackup() throws IOException {
    Path written = backups.writeBackup("before-import");
    var entries = SyncFormatTest.unzip(Files.readAllBytes(written));
    assertThat(entries).containsKey(SyncTables.MANIFEST_ENTRY);
    assertThat(new String(entries.get(SyncTables.MANIFEST_ENTRY))).contains("\"backup\" : true");
    assertThat(backups.resolve(written.getFileName().toString())).contains(written);
    assertThat(backups.resolve("../" + written.getFileName())).isEmpty();
    assertThat(backups.resolve("missing.bak")).isEmpty();
  }
}
