package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.Collision.Type;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The C6 resolutions as the apply carries them out: a "bring back" wins over the tombstone the
 * file carries for the same record, and a "skip" takes the rows that refer to the skipped one
 * with it instead of failing on a foreign key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"sync.backup-dir=target/sync-apply-test", "sync.work-dir=target/sync-apply-work"})
class SyncApplyResolutionTest {

  private static final Instant SINCE = Instant.parse("2026-03-01T12:00:00Z");
  private static final OffsetDateTime AFTER = OffsetDateTime.ofInstant(SINCE.plusSeconds(3600), ZoneOffset.UTC);
  private static final OffsetDateTime LATER = OffsetDateTime.ofInstant(SINCE.plusSeconds(7200), ZoneOffset.UTC);
  private static final UUID NEW_COURSE = UUID.fromString("00000000-0000-0000-0000-000000000031");
  private static final UUID NEW_PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000032");
  private static final UUID NO_SUCH_IMAGE = UUID.fromString("00000000-0000-0000-0000-000000000099");

  @Autowired
  private DataSource dataSource;
  @Autowired
  private SyncExporter exporter;
  @Autowired
  private SyncImportService imports;
  @Autowired
  private SyncNodeService nodeService;

  @Test
  void bringingBackARecordTheFileAlsoDeletedWritesItLive() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side deleted Anna, then (by mistake) booked a new course for her.
    SyncFixture.seed(jdbc);
    SyncFixture.update(jdbc, "update participants set deleted_at = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", AFTER, AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    course(jdbc, NEW_COURSE, 2002L, SyncFixture.PARTICIPANT, AFTER, SyncFixture.ORIGIN);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);

    // This side deleted Anna too, later, so the file's earlier tombstone is the one the plan writes.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncFixture.update(jdbc, "update participants set deleted_at = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", LATER, LATER, self, SyncFixture.PARTICIPANT);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "revive.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    assertThat(analysis.plan()).extracting(SyncAnalysis.RowPlan::key)
        .contains(SyncFixture.PARTICIPANT.toString(), NEW_COURSE.toString());
    String c6 = "C6-courses-" + NEW_COURSE + "-courses_participant_id-" + SyncFixture.PARTICIPANT;
    assertThat(analysis.collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C6);
      assertThat(c.id()).isEqualTo(c6);
    });

    SyncImportResult result = imports.apply(staged, Map.of(c6, "revive"));

    Map<String, Object> anna = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        SyncFixture.PARTICIPANT);
    assertThat(anna.get("DELETED_AT")).isNull();
    assertThat(((Number) anna.get("VERSION")).longValue()).isEqualTo(2L);
    assertThat(String.valueOf(anna.get("ORIGIN_NODE"))).isEqualTo(self.toString());
    assertThat(jdbc.queryForObject("select count(*) from courses where courses_uuid = ?", Long.class, NEW_COURSE))
        .isEqualTo(1L);
    assertThat(result.report().changes()).anySatisfy(change -> assertThat(change).contains("brought back"));
  }

  @Test
  void skippingARecordAlsoSkipsTheRecordsThatReferToIt() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // The other side: a new participant whose photo exists nowhere, and a course for that participant.
    SyncFixture.seed(jdbc);
    jdbc.update("insert into image (image_uuid, image_content_type, created_at, updated_at, version, origin_node)"
        + " values (?, 'image/png', ?, ?, 0, ?)", NO_SUCH_IMAGE, AFTER, AFTER, SyncFixture.ORIGIN);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, image_uuid, created_at, updated_at, version, origin_node)"
        + " values (?, ?, ?, ?, ?, ?, ?, 0, ?)", NEW_PARTICIPANT, 1002L, "Ola", "New", NO_SUCH_IMAGE, AFTER, AFTER,
        SyncFixture.ORIGIN);
    course(jdbc, NEW_COURSE, 2002L, NEW_PARTICIPANT, AFTER, SyncFixture.ORIGIN);
    jdbc.update("insert into courses_trainers (courses_uuid, trainer_uuid) values (?, ?)", NEW_COURSE,
        SyncFixture.TRAINER);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    file = without(file, "tables/image.jsonl", NO_SUCH_IMAGE);

    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "skip.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c6 = "C6-participants-" + NEW_PARTICIPANT + "-image_uuid-" + NO_SUCH_IMAGE;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactly(c6);

    SyncImportResult result = imports.apply(staged, Map.of(c6, "skip-row"));

    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("select count(*) from courses", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("select count(*) from courses_trainers", Long.class)).isEqualTo(1L);
    assertThat(result.report().changes()).hasSize(2)
        .anySatisfy(change -> assertThat(change).contains("skipped Participant Ola New"))
        .anySatisfy(change -> assertThat(change).contains("skipped course").contains("was skipped"));
    assertThat(result.report().inserted()).isZero();
  }

  @Test
  void skippingARecordThatIsDeletedHereAlsoSkipsTheRecordsThatReferToIt() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side brought the participant back (version 2) with a photo that exists nowhere,
    // and booked a course for them.
    SyncFixture.seed(jdbc);
    jdbc.update("insert into image (image_uuid, image_content_type, created_at, updated_at, version, origin_node)"
        + " values (?, 'image/png', ?, ?, 0, ?)", NO_SUCH_IMAGE, AFTER, AFTER, SyncFixture.ORIGIN);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, image_uuid, created_at, updated_at, version, origin_node)"
        + " values (?, ?, ?, ?, ?, ?, ?, 2, ?)", NEW_PARTICIPANT, 1002L, "Ola", "New", NO_SUCH_IMAGE, AFTER, AFTER,
        SyncFixture.ORIGIN);
    course(jdbc, NEW_COURSE, 2002L, NEW_PARTICIPANT, AFTER, SyncFixture.ORIGIN);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    file = without(file, "tables/image.jsonl", NO_SUCH_IMAGE);

    // This side knows the participant only as a tombstone (deleted before the last sync) and has no course.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    OffsetDateTime before = OffsetDateTime.ofInstant(SINCE.minusSeconds(3600), ZoneOffset.UTC);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, image_uuid, created_at, updated_at, deleted_at, version, origin_node)"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)", NEW_PARTICIPANT, 1002L, "Ola", "New", null, before, before,
        before, self);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "tombstone.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c6 = "C6-participants-" + NEW_PARTICIPANT + "-image_uuid-" + NO_SUCH_IMAGE;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactly(c6);

    SyncImportResult result = imports.apply(staged, Map.of(c6, "skip-row"));

    // The skipped row stays a tombstone here, so the course that refers to it must not be written.
    assertThat(jdbc.queryForObject("select deleted_at from participants where participants_uuid = ?", Object.class,
        NEW_PARTICIPANT)).isNotNull();
    assertThat(jdbc.queryForObject("select count(*) from courses", Long.class)).isEqualTo(1L);
    assertThat(result.report().changes())
        .anySatisfy(change -> assertThat(change).contains("skipped course").contains("was skipped"));
    assertThat(result.report().inserted()).isZero();
  }

  private static void course(JdbcTemplate jdbc, UUID id, long number, UUID participant, OffsetDateTime at,
      UUID origin) {
    SyncFixture.update(jdbc, "insert into courses (courses_uuid, courses_id, courses_start_date, courses_end_date,"
        + " courses_course_type_uuid, courses_participant_id, course_counter_uuid, created_at, updated_at, version,"
        + " origin_node) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)", id, number, LocalDate.of(2026, 4, 1),
        LocalDate.of(2026, 4, 5), SyncFixture.COURSE_TYPE, participant, null, at, at, origin);
  }

  /** The file without the lines of one table entry that mention {@code id}. */
  private static byte[] without(byte[] file, String entry, UUID id) throws Exception {
    Map<String, byte[]> entries = SyncFormatTest.unzip(file);
    String kept = String.join("\n", List.of(new String(entries.get(entry)).split("\n")).stream()
        .filter(line -> !line.contains(id.toString())).toList());
    entries.put(entry, kept.isEmpty() ? new byte[0] : (kept + "\n").getBytes());
    return SyncFixture.zip(entries);
  }
}
