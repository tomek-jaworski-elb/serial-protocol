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

  @Test
  void skippingAnUpdateOfARecordThatIsLiveHereKeepsTheRecordsThatReferToIt() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // The other side gave Anna a photo that exists nowhere and booked a new course for her.
    SyncFixture.seed(jdbc);
    jdbc.update("insert into image (image_uuid, image_content_type, created_at, updated_at, version, origin_node)"
        + " values (?, 'image/png', ?, ?, 0, ?)", NO_SUCH_IMAGE, AFTER, AFTER, SyncFixture.ORIGIN);
    SyncFixture.update(jdbc, "update participants set image_uuid = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", NO_SUCH_IMAGE, AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    course(jdbc, NEW_COURSE, 2002L, SyncFixture.PARTICIPANT, AFTER, SyncFixture.ORIGIN);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    file = without(file, "tables/image.jsonl", NO_SUCH_IMAGE);

    // This side still has Anna as she was: live, untouched since the last exchange.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "live.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c6 = "C6-participants-" + SyncFixture.PARTICIPANT + "-image_uuid-" + NO_SUCH_IMAGE;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactly(c6);

    SyncImportResult result = imports.apply(staged, Map.of(c6, "skip-row"));

    // Anna stays as she is here, and the course that refers to her is still written.
    Map<String, Object> anna = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        SyncFixture.PARTICIPANT);
    assertThat(String.valueOf(anna.get("IMAGE_UUID"))).isEqualTo(SyncFixture.IMAGE.toString());
    assertThat(((Number) anna.get("VERSION")).longValue()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from courses where courses_uuid = ?", Long.class, NEW_COURSE))
        .isEqualTo(1L);
    assertThat(result.report().changes()).singleElement().asString().contains("skipped Participant Anna Nowak");
    assertThat(result.report().inserted()).isEqualTo(1);
  }

  @Test
  void keepingTheLocalVersionLeavesTheDanglingReferenceOfTheFileSideAlone() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side gave Anna a photo that the file will not carry.
    SyncFixture.seed(jdbc);
    jdbc.update("insert into image (image_uuid, image_content_type, created_at, updated_at, version, origin_node)"
        + " values (?, 'image/png', ?, ?, 0, ?)", NO_SUCH_IMAGE, AFTER, AFTER, SyncFixture.ORIGIN);
    SyncFixture.update(jdbc, "update participants set image_uuid = ?, updated_at = ?, version = 1, origin_node = ?"
        + " where participants_uuid = ?", NO_SUCH_IMAGE, AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    byte[] file = without(SyncFixture.fileFromOtherNode(exporter), "tables/image.jsonl", NO_SUCH_IMAGE);

    // This side edited Anna too.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncFixture.update(jdbc, "update participants set participants_email = ?, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", "here@example", LATER, self, SyncFixture.PARTICIPANT);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "keep.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c2 = "C2-participants-" + SyncFixture.PARTICIPANT;
    String c6 = "C6-participants-" + SyncFixture.PARTICIPANT + "-image_uuid-" + NO_SUCH_IMAGE;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(c2, c6);

    SyncImportResult result = imports.apply(staged, Map.of(c2, "keep-local", c6, "skip-row"));

    Map<String, Object> anna = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        SyncFixture.PARTICIPANT);
    assertThat(anna.get("PARTICIPANTS_EMAIL")).isEqualTo("here@example");
    assertThat(String.valueOf(anna.get("IMAGE_UUID"))).isEqualTo(SyncFixture.IMAGE.toString());
    assertThat(result.report().changes()).as("nothing was skipped: the file's Anna was never taken").isEmpty();
    assertThat(result.report().updated()).isZero();
  }

  @Test
  void keepingTheFileNumberWhenItsLocalHolderIsReplacedByTheFileNeedsNoRenumbering() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side moved Anna to number 1005 and gave 1001 to a new participant.
    SyncFixture.seed(jdbc);
    SyncFixture.update(jdbc, "update participants set participants_id = 1005, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, created_at, updated_at, version, origin_node) values (?, ?, ?, ?, ?, ?, 0, ?)",
        NEW_PARTICIPANT, 1001L, "Ola", "New", AFTER, AFTER, SyncFixture.ORIGIN);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);

    // This side edited Anna, who still holds 1001 here.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncFixture.update(jdbc, "update participants set participants_email = ?, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", "here@example", LATER, self, SyncFixture.PARTICIPANT);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "number.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c2 = "C2-participants-" + SyncFixture.PARTICIPANT;
    String c4 = "C4-participants-" + NEW_PARTICIPANT;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(c2, c4);

    SyncImportResult result = imports.apply(staged, Map.of(c2, "take-file", c4, "keep-file-number"));

    assertThat(jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, SyncFixture.PARTICIPANT)).isEqualTo(1005L);
    assertThat(jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, NEW_PARTICIPANT)).isEqualTo(1001L);
    assertThat(result.report().changes()).singleElement().asString()
        .contains("number 1001").contains("replaced by the file's copy");
  }

  @Test
  void keepingTheFileNumberRenumbersTheLocalHolderWhenItsFileCopyEndsUpSkipped() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // The other side moved Anna to 1005, gave her a photo the file will not carry, and 1001 to Ola.
    SyncFixture.seed(jdbc);
    jdbc.update("insert into image (image_uuid, image_content_type, created_at, updated_at, version, origin_node)"
        + " values (?, 'image/png', ?, ?, 0, ?)", NO_SUCH_IMAGE, AFTER, AFTER, SyncFixture.ORIGIN);
    SyncFixture.update(jdbc, "update participants set participants_id = 1005, image_uuid = ?, updated_at = ?,"
        + " version = 1, origin_node = ? where participants_uuid = ?", NO_SUCH_IMAGE, AFTER, SyncFixture.ORIGIN,
        SyncFixture.PARTICIPANT);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, created_at, updated_at, version, origin_node) values (?, ?, ?, ?, ?, ?, 0, ?)",
        NEW_PARTICIPANT, 1001L, "Ola", "New", AFTER, AFTER, SyncFixture.ORIGIN);
    byte[] file = without(SyncFixture.fileFromOtherNode(exporter), "tables/image.jsonl", NO_SUCH_IMAGE);

    // This side edited Anna, who still holds 1001 here.
    SyncFixture.seed(jdbc);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncFixture.update(jdbc, "update participants set participants_email = ?, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", "here@example", LATER, self, SyncFixture.PARTICIPANT);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "skipped.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String c2 = "C2-participants-" + SyncFixture.PARTICIPANT;
    String c4 = "C4-participants-" + NEW_PARTICIPANT;
    String c6 = "C6-participants-" + SyncFixture.PARTICIPANT + "-image_uuid-" + NO_SUCH_IMAGE;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(c2, c4, c6);

    // The file's Anna is taken and then skipped for her missing photo: the local Anna stays and
    // still holds 1001, so she has to move for Ola to get it.
    SyncImportResult result = imports.apply(staged, Map.of(c2, "take-file", c6, "skip-row", c4, "keep-file-number"));

    Map<String, Object> anna = jdbc.queryForMap("select * from participants where participants_uuid = ?",
        SyncFixture.PARTICIPANT);
    assertThat(anna.get("PARTICIPANTS_EMAIL")).isEqualTo("here@example");
    assertThat(((Number) anna.get("PARTICIPANTS_ID")).longValue()).isGreaterThan(1005L);
    assertThat(jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, NEW_PARTICIPANT)).isEqualTo(1001L);
    assertThat(result.report().changes()).anySatisfy(change -> assertThat(change).contains("skipped Participant"))
        .anySatisfy(change -> assertThat(change).contains("made here was given number"));
  }

  @Test
  void twoParticipantsWhoSwappedNumbersInTheFileAreWrittenInAnOrderTheUniqueIndexAllows() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();

    // Both sides know Anna (1001) and Ola (1002); the other side swapped their numbers.
    SyncFixture.seed(jdbc);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, created_at, updated_at, version, origin_node) values (?, ?, ?, ?, ?, ?, 0, ?)",
        NEW_PARTICIPANT, 1002L, "Ola", "New", SyncFixture.AT, SyncFixture.AT, SyncFixture.ORIGIN);
    jdbc.update("update participants set participants_id = 9999 where participants_uuid = ?", NEW_PARTICIPANT);
    SyncFixture.update(jdbc, "update participants set participants_id = 1002, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", AFTER, SyncFixture.ORIGIN, SyncFixture.PARTICIPANT);
    SyncFixture.update(jdbc, "update participants set participants_id = 1001, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", AFTER, SyncFixture.ORIGIN, NEW_PARTICIPANT);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);

    // This side edited both, so both are C2; taking the file's side of each swaps their numbers.
    SyncFixture.seed(jdbc);
    SyncFixture.update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name,"
        + " participants_surname, created_at, updated_at, version, origin_node) values (?, ?, ?, ?, ?, ?, 0, ?)",
        NEW_PARTICIPANT, 1002L, "Ola", "New", SyncFixture.AT, SyncFixture.AT, SyncFixture.ORIGIN);
    jdbc.update("update sync_node set last_sync_at = ?", OffsetDateTime.ofInstant(SINCE, ZoneOffset.UTC));
    SyncFixture.update(jdbc, "update participants set participants_email = ?, updated_at = ?, version = 1,"
        + " origin_node = ?", "here@example", LATER, self);

    SyncStaging.Staged staged = imports.staging().stage(new ByteArrayInputStream(file), "swap.sync");
    SyncAnalysis analysis = imports.analyze(staged).analysis();
    String anna = "C2-participants-" + SyncFixture.PARTICIPANT;
    String ola = "C2-participants-" + NEW_PARTICIPANT;
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(anna, ola);

    imports.apply(staged, Map.of(anna, "take-file", ola, "take-file"));

    assertThat(jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, SyncFixture.PARTICIPANT)).isEqualTo(1002L);
    assertThat(jdbc.queryForObject("select participants_id from participants where participants_uuid = ?",
        Long.class, NEW_PARTICIPANT)).isEqualTo(1001L);
    assertThat(jdbc.queryForObject("select count(*) from participants", Long.class)).isEqualTo(2L);
  }

  @Test
  void aRowFromTheLastFileOfTheSameNodeEditedHereSinceIsNotACollisionWithTheNextFile() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID self = nodeService.current().getId();
    SyncFixture.seed(jdbc);
    byte[] file = SyncFixture.fileFromOtherNode(exporter);
    SyncFixture.wipe(jdbc);
    SyncStaging.Staged first = imports.staging().stage(new ByteArrayInputStream(file), "first.sync");
    imports.apply(first, Map.of());
    Map<String, Object> node = jdbc.queryForMap("select * from sync_node");
    assertThat(String.valueOf(node.get("LAST_IMPORT_NODE"))).isEqualTo(SyncFixture.ORIGIN.toString());
    assertThat(node.get("LAST_IMPORT_EXPORTED_AT")).isNotNull();

    // Anna, written there, is edited here after the import; the next file still has their copy.
    SyncFixture.update(jdbc, "update participants set participants_email = ?, updated_at = ?, version = 1,"
        + " origin_node = ? where participants_uuid = ?", "here@example", OffsetDateTime.now().plusMinutes(1), self,
        SyncFixture.PARTICIPANT);
    SyncStaging.Staged second = imports.staging().stage(new ByteArrayInputStream(file), "second.sync");
    SyncAnalysis analysis = imports.analyze(second).analysis();

    assertThat(analysis.hasCollisions()).isFalse();
    assertThat(analysis.plan()).isEmpty();
    imports.apply(second, Map.of());
    assertThat(jdbc.queryForObject("select participants_email from participants where participants_uuid = ?",
        String.class, SyncFixture.PARTICIPANT)).isEqualTo("here@example");
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
