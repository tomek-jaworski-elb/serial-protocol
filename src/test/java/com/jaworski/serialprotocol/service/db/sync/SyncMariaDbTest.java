package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 6's acceptance test (docs/sync-spec.md § Testing): the round trip on the production
 * database. H2 reports a UUID column as BINARY(16) and MariaDB as CHAR, a DATETIME(6) comes back
 * through the MariaDB driver as a plain TIMESTAMP, and the Flyway schema (not the one Hibernate
 * generates) is what the file is written from and loaded into. With a three-digit number of
 * photos the export, the import and the restore all stream the blobs instead of holding them.
 *
 * <p>Skipped when Docker is not available, like {@code SchemaMigrationTest}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "sync.backup-dir=target/sync-mariadb-test",
    "sync.work-dir=target/sync-mariadb-work"
})
class SyncMariaDbTest {

  /** Same image as docker-compose.yml, so the test and the deployment agree on the server. */
  @Container
  @ServiceConnection
  static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.8");

  private static final int PHOTOS = 120;
  private static final int PHOTO_SIZE = 64 * 1024;

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncExporter exporter;
  @Autowired
  private SyncLocalReader localReader;
  @Autowired
  private SyncImportService imports;
  @Autowired
  private SyncBackupService backups;
  @Autowired
  private SyncRestoreService restores;
  @Autowired
  private SyncNodeService nodeService;

  @Test
  void theRoundTripOnMariaDbKeepsEveryRowAndEveryPhoto() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    List<UUID> photos = addPhotos(jdbc);
    Map<String, List<SyncRow>> before = localReader.read();
    assertThat(before.get("image")).hasSize(PHOTOS + 1);
    assertThat(exporter.schemaVersion()).as("the file is made from the Flyway schema").isNotNull();

    // The types the spec singles out: a UUID column and the audit timestamps, read back as text.
    Map<String, Object> participant = before.get("participants").getFirst().values();
    assertThat(participant.get("participants_uuid")).isEqualTo(SyncFixture.PARTICIPANT.toString());
    assertThat(participant.get("origin_node")).isEqualTo(SyncFixture.ORIGIN.toString());
    assertThat(participant.get("created_at")).isEqualTo("2026-01-02T03:04:05.123456Z");
    assertThat(participant.get("participants_birth_date")).isEqualTo("1990-05-17");
    assertThat(before.get("student").getFirst().values().get("student_date_begine"))
        .isEqualTo("2026-01-02T03:04:05.123456Z");

    // Export as another installation would, then load the file into an empty database.
    Path file = Files.createDirectories(Path.of("target/sync-mariadb-work")).resolve("other.sync");
    jdbc.update("update sync_node set node_id = ?, node_name = 'Other'", UUID.randomUUID());
    try (OutputStream out = Files.newOutputStream(file)) {
      exporter.export(out, false);
    }
    jdbc.update("update sync_node set node_id = ?, node_name = 'Self'", UUID.randomUUID());
    try (ZipFile zip = new ZipFile(file.toFile())) {
      assertThat(zip.stream().map(ZipEntry::getName).filter(n -> n.startsWith("blobs/")).count())
          .isEqualTo(PHOTOS + 1);
    }
    SyncFixture.wipe(jdbc);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(Files.readAllBytes(file)),
        "other.sync");
    SyncImportResult imported = imports.apply(staged, Map.of());

    assertThat(imported.report().blobs()).isEqualTo(PHOTOS + 1);
    assertThat(localReader.read()).isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from image_blob", Long.class)).isEqualTo(PHOTOS + 1L);
    assertPhotosIntact(jdbc, photos);
    assertThat(jdbc.queryForObject("select image_thumb_data from image_blob where image_uuid = ?", byte[].class,
        SyncFixture.IMAGE)).isEqualTo(SyncFixture.THUMB);
    assertThat(jdbc.queryForObject("select id_sequence_next from id_sequence where id_sequence_name = 'participant'",
        Long.class)).isEqualTo(1002L);
    assertThat(nodeService.current().getLastImportFile()).isEqualTo("other.sync");

    // A backup of this state, damage, then the restore: the replace path on the same driver.
    Path backup = backups.writeBackup("manual");
    jdbc.update("update image_blob set image_data = ?", new byte[] {0});
    jdbc.update("delete from courses_trainers");
    jdbc.update("update participants set participants_email = 'wrong@example', version = 9");
    assertThat(localReader.read()).isNotEqualTo(before);

    SyncRestoreResult restored = restores.restore(backup);

    assertThat(restored.blobs()).isEqualTo(PHOTOS + 1);
    assertThat(localReader.read()).isEqualTo(before);
    assertPhotosIntact(jdbc, photos);
  }

  /** A three-digit number of distinct photos, each larger than one fetch buffer's worth of row data. */
  private static List<UUID> addPhotos(JdbcTemplate jdbc) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < PHOTOS; i++) {
      UUID id = UUID.fromString(String.format("00000000-0000-0000-0001-%012d", i));
      ids.add(id);
      SyncFixture.update(jdbc, "insert into image (image_uuid, image_content_type, image_thumb_content_type, image_thumb_version,"
          + " created_at, updated_at, deleted_at, version, origin_node) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          id, "image/png", i % 2 == 0 ? "image/jpeg" : null, "v1", SyncFixture.AT, SyncFixture.AT, null, 0L,
          SyncFixture.ORIGIN);
      jdbc.update("insert into image_blob (image_uuid, image_data, image_thumb_data) values (?, ?, ?)",
          id, photo(i), i % 2 == 0 ? new byte[] {(byte) i} : null);
    }
    return ids;
  }

  private static byte[] photo(int i) {
    byte[] bytes = new byte[PHOTO_SIZE];
    new Random(i).nextBytes(bytes);
    return bytes;
  }

  private static void assertPhotosIntact(JdbcTemplate jdbc, List<UUID> photos) {
    for (int i = 0; i < photos.size(); i++) {
      assertThat(jdbc.queryForObject("select image_data from image_blob where image_uuid = ?", byte[].class,
          photos.get(i))).as("photo %d", i).isEqualTo(photo(i));
    }
    assertThat(jdbc.queryForObject("select count(*) from image_blob where image_thumb_data is null", Long.class))
        .isEqualTo(PHOTOS / 2L);
  }
}
