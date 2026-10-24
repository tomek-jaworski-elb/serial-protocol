package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Once the import or restore transaction has committed, a failure in the housekeeping that
 * follows (pruning, thumbnails) is logged and the operation still reports what it wrote;
 * otherwise the user would see a failure for rows that were changed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "sync.backup-dir=target/sync-housekeeping-test", "sync.work-dir=target/sync-housekeeping-work"})
class SyncHousekeepingTest {

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncExporter exporter;
  @Autowired
  private SyncImportService imports;
  @Autowired
  private SyncRestoreService restores;
  @Autowired
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
