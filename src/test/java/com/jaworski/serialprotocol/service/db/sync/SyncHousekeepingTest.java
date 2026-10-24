package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Once the import or restore transaction has committed, a failure in the housekeeping that
 * follows (the report file, discarding the upload, pruning, thumbnails) is logged and the
 * operation still reports what it wrote; otherwise the user would see a failure for rows that
 * were changed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "sync.backup-dir=target/sync-housekeeping-test", "sync.work-dir=target/sync-housekeeping-work"})
class SyncHousekeepingTest {

  private static final UUID NEW_PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000041");

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncExporter exporter;
  @Autowired
  private SyncImportService imports;
  @Autowired
  private SyncRestoreService restores;
  @MockitoSpyBean
  private SyncBackupService backups;
  @MockitoBean
  private ThumbnailBackfill thumbnails;

  @BeforeEach
  void cleanDirectory() throws IOException {
    FileSystemUtils.deleteRecursively(backups.directory());
    given(thumbnails.refreshStale()).willThrow(new IllegalStateException("thumbnails are broken"));
  }

  @Test
  void anImportSucceedsWhenTheThumbnailRebuildFails() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    SyncFixture.wipe(jdbc);
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other.sync");

    SyncImportResult result = imports.apply(staged, Map.of());

    assertThat(result.thumbnailsRebuilt()).isZero();
    assertThat(result.report().inserted()).isPositive();
    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(1L);
    assertThat(imports.staging().find(staged.token())).isEmpty();
  }

  @Test
  void anImportSucceedsWhenItsReportCannotBeWritten() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    SyncFixture.wipe(jdbc);
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other.sync");
    // A directory where the report file should go makes the write fail after the commit.
    willAnswer(invocation -> {
      Path backup = (Path) invocation.callRealMethod();
      Files.createDirectory(SyncBackupService.reportFor(backup));
      return backup;
    }).given(backups).writeBackup("before-import");

    SyncImportResult result = imports.apply(staged, Map.of());

    assertThat(result.report().inserted()).isPositive();
    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(1L);
    assertThat(Files.isDirectory(result.reportFile())).isTrue();
    assertThat(imports.staging().find(staged.token())).isEmpty();
  }

  @Test
  void anEditMadeWhileTheBackupIsWrittenRefusesTheImport() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    SyncFixture.wipe(jdbc);
    jdbc.update("update sync_node set last_import_file = 'earlier.sync'");
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other.sync");
    // The report was analysed against an empty database; a participant arrives during the backup.
    willAnswer(invocation -> {
      Path backup = (Path) invocation.callRealMethod();
      jdbc.update("insert into participants (participants_uuid, participants_id, participants_name,"
          + " participants_surname, created_at, updated_at, version, origin_node) values (?, 1002, 'Ola', 'New',"
          + " ?, ?, 0, ?)", NEW_PARTICIPANT, SyncFixture.AT, SyncFixture.AT, SyncFixture.ORIGIN);
      return backup;
    }).given(backups).writeBackup("before-import");

    assertThatThrownBy(() -> imports.apply(staged, Map.of()))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("changed while the backup was being written");

    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("select count(*) from courses", Long.class)).isZero();
    assertThat(jdbc.queryForObject("select last_import_file from sync_node", String.class)).isEqualTo("earlier.sync");
    assertThat(imports.staging().find(staged.token())).as("the upload stays for another try").isPresent();
  }

  @Test
  void aRestoreSucceedsWhenTheThumbnailRebuildFails() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Path backup = backups.writeBackup("manual");
    jdbc.update("update participants set participants_email = ? where participants_uuid = ?", "wrong@example",
        SyncFixture.PARTICIPANT);

    SyncRestoreResult result = restores.restore(backup);

    assertThat(result.thumbnailsRebuilt()).isZero();
    assertThat(jdbc.queryForObject("select participants_email from participants where participants_uuid = ?",
        String.class, SyncFixture.PARTICIPANT)).isEqualTo("anna@example.com");
    assertThat(backups.list()).hasSize(2);
  }
}
