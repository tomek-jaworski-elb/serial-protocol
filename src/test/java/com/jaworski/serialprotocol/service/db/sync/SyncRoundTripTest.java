package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.Collision.Type;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4's acceptance test (docs/sync-spec.md): an export loaded into an empty database
 * gives back the same rows, sequences and blobs; then both sides edit and the second import
 * resolves the collisions the way the user chose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"sync.backup-dir=target/sync-roundtrip-test", "sync.work-dir=target/sync-roundtrip-work"})
class SyncRoundTripTest {

  private static final Instant SINCE = Instant.parse("2026-03-01T12:00:00Z");
  private static final OffsetDateTime AFTER = OffsetDateTime.ofInstant(SINCE.plusSeconds(3600), ZoneOffset.UTC);
  private static final UUID FILE_PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID LOCAL_PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000012");

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncExporter exporter;
  @Autowired
  private SyncLocalReader localReader;
  @Autowired
  private SyncImportService imports;
  @Autowired
  private SyncNodeService nodeService;

  @Test
  void anExportImportedIntoAnEmptyDatabaseComesBackIdentical() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    Map<String, List<SyncRow>> before = localReader.read();
    byte[] file = fileFromOtherNode();

    SyncFixture.wipe(jdbc);
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other.sync");
    SyncImportResult result = imports.apply(staged, Map.of());

    assertThat(localReader.read()).isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(1L);
    assertThat(sequence(jdbc, "participant")).isEqualTo(1002L);
    assertThat(sequence(jdbc, "courses")).isEqualTo(2002L);
    assertThat(sequence(jdbc, "course_counter")).isEqualTo(8L);
    assertThat(jdbc.queryForObject("select image_data from image_blob where image_uuid = ?", byte[].class,
        SyncFixture.IMAGE)).isEqualTo(SyncFixture.PHOTO);
    assertThat(jdbc.queryForObject("select image_thumb_data from image_blob where image_uuid = ?", byte[].class,
        SyncFixture.IMAGE)).isEqualTo(SyncFixture.THUMB);
    assertThat(jdbc.queryForObject("select count(*) from courses_trainers where courses_uuid = ?", Long.class,
        SyncFixture.COURSE)).isEqualTo(1L);

    assertThat(result.report().inserted()).isEqualTo(before.values().stream().mapToInt(List::size).sum()
        - before.get("id_sequence").size()
        - before.get("trainer_image").size() - before.get("lecturer_image").size()
        - before.get("technician_image").size() - before.get("courses_trainers").size()
        - before.get("courses_lecturers").size() - before.get("courses_technicians").size());
    assertThat(result.report().blobs()).isEqualTo(1);
    assertThat(result.backup()).exists();
    assertThat(result.reportFile()).exists().hasFileName(result.backup().getFileName() + ".report.json");
    assertThat(Files.readString(result.reportFile())).contains("\"file\" : \"other.sync\"").contains("Other");
    assertThat(nodeService.current().getLastImportFile()).isEqualTo("other.sync");
    assertThat(nodeService.current().getLastImportAt()).isNotNull();
    assertThat(nodeService.current().getLastSyncAt()).isNotNull();
    assertThat(imports.staging().find(staged.token())).isEmpty();
  }

  @Test
  void editsOnBothSidesAreResolvedTheWayTheUserChose() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side: Anna's email changed, a new participant 1002.
    SyncFixture.seed(jdbc);
    jdbc.update("update participants set participants_email = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", "anna@other.example", AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    participant(jdbc, FILE_PARTICIPANT, 1002L, "Ola", SyncFixture.ORIGIN);
    jdbc.update("update id_sequence set id_sequence_next = 1003 where id_sequence_name = 'participant'");
    byte[] file = fileFromOtherNode();

    // This side, since the last sync: Anna's phone changed, another new participant 1002.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    jdbc.update("update participants set participants_phone_number = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", "+48 700 000 000", AFTER, self, SyncFixture.PARTICIPANT);
    participant(jdbc, LOCAL_PARTICIPANT, 1002L, "Ula", self);
    jdbc.update("update id_sequence set id_sequence_next = 1003 where id_sequence_name = 'participant'");

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other-2.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    assertThat(analysis.collisions()).extracting(Collision::type, Collision::table)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(Type.C2, "participants"),
            org.assertj.core.groups.Tuple.tuple(Type.C4, "participants"));
    String c2 = "C2-participants-" + SyncFixture.PARTICIPANT;
    String c4 = "C4-participants-" + FILE_PARTICIPANT;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(c2, c4);

    SyncImportResult result = imports.apply(staged, Map.of(c2, "take-file", c4, "keep-file-number"));

    Map<String, Object> anna = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        SyncFixture.PARTICIPANT);
    assertThat(anna.get("PARTICIPANTS_EMAIL")).isEqualTo("anna@other.example");
    assertThat(anna.get("PARTICIPANTS_PHONE_NUMBER")).isEqualTo("+48 600 000 000");
    assertThat(((Number) anna.get("VERSION")).longValue()).isEqualTo(1L);
    assertThat(String.valueOf(anna.get("ORIGIN_NODE"))).isEqualTo(SyncFixture.ORIGIN.toString());

    assertThat(number(jdbc, FILE_PARTICIPANT)).isEqualTo(1002L);
    Map<String, Object> ula = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        LOCAL_PARTICIPANT);
    assertThat(((Number) ula.get("PARTICIPANTS_ID")).longValue()).isEqualTo(1003L);
    assertThat(((Number) ula.get("VERSION")).longValue()).isEqualTo(1L);
    assertThat(String.valueOf(ula.get("ORIGIN_NODE"))).isEqualTo(self.toString());
    assertThat(sequence(jdbc, "participant")).isEqualTo(1004L);

    assertThat(result.report().collisions()).extracting(SyncImportReport.ResolvedCollision::id,
            SyncImportReport.ResolvedCollision::resolution)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(c2, "take-file"),
            org.assertj.core.groups.Tuple.tuple(c4, "keep-file-number"));
    assertThat(result.report().changes()).anySatisfy(change -> assertThat(change).contains("1003"));
    String json = Files.readString(result.reportFile());
    assertThat(json).contains(c2).contains(c4).contains("take-file").contains("keep-file-number");
  }

  @Test
  void anImportWithAnUnresolvedCollisionIsRefusedAndChangesNothing() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    jdbc.update("update participants set participants_email = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", "anna@other.example", AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    byte[] file = fileFromOtherNode();
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    jdbc.update("update participants set participants_phone_number = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", "+48 700 000 000", AFTER, nodeService.current().getId(),
        SyncFixture.PARTICIPANT);
    Map<String, List<SyncRow>> before = localReader.read();
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "other-3.sync");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> imports.apply(staged, Map.of()))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("every collision");

    assertThat(localReader.read()).isEqualTo(before);
    assertThat(imports.staging().find(staged.token())).isPresent();
  }

  private static void participant(JdbcTemplate jdbc, UUID id, long number, String name, UUID origin) {
    jdbc.update("insert into participants (participants_uuid, participants_id, participants_name, participants_surname,"
        + " created_at, updated_at, deleted_at, version, origin_node) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id, number, name, "New", AFTER, AFTER, null, 0L, origin);
  }

  private static long sequence(JdbcTemplate jdbc, String name) {
    Long value = jdbc.queryForObject("select id_sequence_next from id_sequence where id_sequence_name = ?",
        Long.class, name);
    return value == null ? -1 : value;
  }

  private static long number(JdbcTemplate jdbc, UUID participant) {
    Long value = jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, participant);
    return value == null ? -1 : value;
  }

  /** The current database exported and re-labelled as another node's file. */
  private byte[] fileFromOtherNode() throws Exception {
    ByteArrayOutputStream export = new ByteArrayOutputStream();
    exporter.export(export, false);
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(export.toByteArray()))) {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8)
        .replaceAll("\"id\" : \"[0-9a-f-]+\"", "\"id\" : \"" + SyncFixture.ORIGIN + "\"")
        .replaceAll("\"name\" : \"[^\"]*\"", "\"name\" : \"Other\"");
    entries.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}
