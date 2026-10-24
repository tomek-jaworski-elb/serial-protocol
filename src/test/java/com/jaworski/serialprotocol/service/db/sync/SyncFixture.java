package com.jaworski.serialprotocol.service.db.sync;

import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * One deterministic row in every synchronised table, so an export is byte-for-byte
 * reproducible and can be held against the golden files under {@code golden/sync/}.
 *
 * <p>Seeded through JDBC, never through the entities: the point is to control the audit
 * columns, {@code version} and {@code origin_node} that an entity save would overwrite. The
 * audit instants go through {@link #update}, because the MariaDB driver would otherwise store
 * an {@link OffsetDateTime} as local wall-clock time and the same seed would read back
 * differently from H2.</p>
 */
final class SyncFixture {

  static final UUID ORIGIN = uuid(9);
  static final UUID COURSE_TYPE = uuid(1);
  static final UUID IMAGE = uuid(2);
  static final UUID PARTICIPANT = uuid(3);
  static final UUID COUNTER = uuid(4);
  static final UUID TRAINER = uuid(5);
  static final UUID LECTURER = uuid(6);
  static final UUID TECHNICIAN = uuid(7);
  static final UUID COURSE = uuid(8);
  static final OffsetDateTime AT = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000, ZoneOffset.UTC);
  static final byte[] PHOTO = {1, 2, 3, 4, 5};
  static final byte[] THUMB = {9, 8};

  /** Every table the fixture touches, children first, so a wipe can run in list order. */
  static final List<String> WIPE_ORDER = List.of(
      "courses_technicians", "courses_lecturers", "courses_trainers", "courses",
      "technician_image", "lecturer_image", "trainer_image", "technician", "lecturer", "trainer",
      "course_counter", "participants", "image_blob", "image", "course_type", "student", "instructor",
      "id_sequence");

  static UUID uuid(int n) {
    return UUID.fromString("00000000-0000-0000-0000-00000000000" + n);
  }

  static void wipe(JdbcTemplate jdbc) {
    for (String table : WIPE_ORDER) {
      jdbc.update("delete from " + table);
    }
  }

  static void seed(JdbcTemplate jdbc) {
    wipe(jdbc);
    String audit = "created_at, updated_at, deleted_at, version, origin_node";
    Object[] auditValues = {AT, AT, null, 0L, ORIGIN};

    update(jdbc, "insert into course_type (course_type_uuid, course_type_code, course_type_description,"
        + " course_type_long_description, " + audit + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(COURSE_TYPE, "SYNC", "Sync fixture course", "Long description with \"quotes\" and ünïcödé", auditValues));
    update(jdbc, "insert into image (image_uuid, image_content_type, image_thumb_content_type, image_thumb_version, "
        + audit + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(IMAGE, "image/png", "image/jpeg", "v1", auditValues));
    update(jdbc, "insert into image_blob (image_uuid, image_data, image_thumb_data) values (?, ?, ?)",
        IMAGE, PHOTO, THUMB);
    update(jdbc, "insert into participants (participants_uuid, participants_id, participants_name, participants_surname,"
        + " participants_nickname, participants_email, participants_phone_number, participants_address,"
        + " participants_notes, participants_birth_date, image_uuid, " + audit + ")"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(PARTICIPANT, 1001L, "Anna", "Nowak", null, "anna@example.com", "+48 600 000 000", "Street 1",
            "line one\nline two", LocalDate.of(1990, 5, 17), IMAGE, auditValues));
    update(jdbc, "insert into course_counter (course_counter_uuid, course_counter_counter, image_uuid, " + audit + ")"
        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
        args(COUNTER, 7L, null, auditValues));
    person(jdbc, "trainer", TRAINER, "Tomasz", auditValues);
    person(jdbc, "lecturer", LECTURER, "Lena", auditValues);
    person(jdbc, "technician", TECHNICIAN, "Tadeusz", auditValues);
    update(jdbc, "insert into trainer_image (trainer_uuid, image_uuid) values (?, ?)", TRAINER, IMAGE);
    update(jdbc, "insert into lecturer_image (lecturer_uuid, image_uuid) values (?, ?)", LECTURER, IMAGE);
    update(jdbc, "insert into technician_image (technician_uuid, image_uuid) values (?, ?)", TECHNICIAN, IMAGE);
    update(jdbc, "insert into courses (courses_uuid, courses_id, courses_start_date, courses_end_date,"
        + " courses_course_type_uuid, courses_participant_id, course_counter_uuid, " + audit + ")"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(COURSE, 2001L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), COURSE_TYPE, PARTICIPANT, COUNTER,
            auditValues));
    update(jdbc, "insert into courses_trainers (courses_uuid, trainer_uuid) values (?, ?)", COURSE, TRAINER);
    update(jdbc, "insert into courses_lecturers (courses_uuid, lecturer_uuid) values (?, ?)", COURSE, LECTURER);
    update(jdbc, "insert into courses_technicians (courses_uuid, technician_uuid) values (?, ?)", COURSE, TECHNICIAN);
    update(jdbc, "insert into student (student_id, student_name, student_last_name, student_mr_ms, student_cert_type,"
        + " student_course_no, student_date_begine, student_date_end, student_photo, " + audit + ")"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(1, "Jan", "Kowalski", "Mr", "STCW", "C-1", AT.toLocalDateTime(), null, PHOTO, auditValues));
    update(jdbc, "insert into instructor (instructor_no, instructor_name, instructor_surname, instructor_nick,"
        + " instructor_mr_ms, instructor_email, instructor_phone, instructor_mobile, instructor_address,"
        + " instructor_postcode, instructor_city, instructor_birth_place, instructor_birth_date, instructor_cert_no,"
        + " instructor_no_certificate, instructor_diploma, instructor_specialization, instructor_expiration_date,"
        + " instructor_notes, instructor_other_notes, instructor_photo1, instructor_photo2, instructor_photo3,"
        + " instructor_photo4, " + audit + ")"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(1, "Ewa", "Wiśniewska", "Ewa", "Ms", "ewa@example.com", "123", "456", "Street 2", "00-001", "Gdynia",
            "Gdańsk", LocalDate.of(1980, 2, 29), "CERT-1", "no", "Diploma", "Navigation", LocalDate.of(2030, 12, 31),
            null, null, PHOTO, null, null, null, auditValues));
    update(jdbc, "insert into id_sequence (id_sequence_name, id_sequence_next) values (?, ?)", "participant", 1002L);
    update(jdbc, "insert into id_sequence (id_sequence_name, id_sequence_next) values (?, ?)", "courses", 2002L);
    update(jdbc, "insert into id_sequence (id_sequence_name, id_sequence_next) values (?, ?)", "course_counter", 8L);
  }

  private static void person(JdbcTemplate jdbc, String table, UUID id, String name, Object[] auditValues) {
    update(jdbc, "insert into " + table + " (" + table + "_uuid, " + table + "_name, " + table + "_surname, "
        + table + "_nickname, " + table + "_email, " + table + "_phone_number, " + table + "_address, "
        + table + "_notes, " + table + "_primary_image_uuid, created_at, updated_at, deleted_at, version, origin_node)"
        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        args(id, name, "Fixture", null, null, null, null, null, IMAGE, auditValues));
  }

  /**
   * {@link JdbcTemplate#update(String, Object...)} with every {@link OffsetDateTime} bound the
   * way Hibernate binds an {@code Instant} on that database: as an offset value on H2, whose
   * audit columns are {@code TIMESTAMP WITH TIME ZONE}, and as a timestamp through a UTC
   * calendar on MariaDB, whose {@code DATETIME(6)} holds the UTC wall-clock time.
   */
  static int update(JdbcTemplate jdbc, String sql, Object... args) {
    return jdbc.update(sql, new ArgumentPreparedStatementSetter(args) {
      @Override
      protected void doSetValue(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof OffsetDateTime at && !isH2(ps)) {
          ps.setTimestamp(index, Timestamp.from(at.toInstant()),
              Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
        } else {
          super.doSetValue(ps, index, value);
        }
      }
    });
  }

  private static boolean isH2(PreparedStatement ps) throws SQLException {
    return ps.getConnection().getMetaData().getDatabaseProductName().startsWith("H2");
  }

  private static Object[] args(Object... values) {
    Object[] audit = (Object[]) values[values.length - 1];
    Object[] all = new Object[values.length - 1 + audit.length];
    System.arraycopy(values, 0, all, 0, values.length - 1);
    System.arraycopy(audit, 0, all, values.length - 1, audit.length);
    return all;
  }

  private SyncFixture() {
  }
}
