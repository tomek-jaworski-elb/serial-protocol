package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.DeletedRecordDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.service.db.custom.CourseCounterService;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.DeletedKind;
import com.jaworski.serialprotocol.service.db.custom.DeletedRecordsService;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.SoftDeleteSupport;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backup and restore with real data: active and deleted rows, photos, a course that points at all of them.
 * Every step commits, because the restore runs each batch in a transaction of its own.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DatabaseBackupService.class, SoftDeleteSupport.class, DeletedRecordsService.class, ParticipantService.class,
    CourseTypeService.class, CourseCounterService.class, CoursesService.class, TrainerService.class, ImageService.class,
    BackupRoundTripTest.JsonConfig.class})
class BackupRoundTripTest {

  @TestConfiguration
  static class JsonConfig {
    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder()
          .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
          .build();
    }
  }

  private static final List<String> TABLES = List.of(
      "courses_trainers", "courses_lecturers", "courses_technicians", "courses", "participants",
      "trainer_image", "lecturer_image", "technician_image", "trainer", "lecturer", "technician",
      "course_counter", "course_type", "image");
  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

  @Autowired
  private DatabaseBackupService backupService;
  @Autowired
  private DeletedRecordsService trash;
  @Autowired
  private ParticipantService participantService;
  @Autowired
  private CourseTypeService courseTypeService;
  @Autowired
  private CourseCounterService courseCounterService;
  @Autowired
  private CoursesService coursesService;
  @Autowired
  private TrainerService trainerService;
  @Autowired
  private ImageService imageService;
  @Autowired
  private PlatformTransactionManager txManager;
  @PersistenceContext
  private EntityManager em;

  private TransactionTemplate tx;

  @BeforeEach
  void clean() {
    tx = new TransactionTemplate(txManager);
    tx.executeWithoutResult(s -> TABLES.forEach(t -> em.createNativeQuery("DELETE FROM " + t).executeUpdate()));
  }

  @Test
  void everythingComesBackAsItWasDeletedRowsIncluded() throws Exception {
    Image photo = imageService.saveImage(new byte[]{1, 2, 3}, "image/png");
    ParticipantDTO active = participantService.save(participant("Active"));
    ParticipantDTO gone = participant("Gone");
    gone.setImage(photo.getId());
    gone = participantService.save(gone);
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CourseTypeDTO goneType = courseTypeService.save(courseType("T-GONE"));
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(7L, null));
    TrainerDTO trainer = trainerService.save(trainer("Tom", Set.of(photo.getId())));
    CoursesDTO course = course(active, type);
    course.setTrainerIds(Set.of(trainer.getId()));
    course.setCourseCounterUuid(counter.uuid());
    course = coursesService.save(course);
    CoursesDTO goneCourse = coursesService.save(course(gone, type));
    // edit once so a version other than 0 exists, then delete a few things
    active.setName("Active edited");
    active = participantService.updateByUuid(active);
    assertEquals(1L, active.getVersion());
    coursesService.deleteByUuid(goneCourse.getUuid());
    participantService.deleteByUuid(gone.getParticipantUuid());
    courseTypeService.deleteById(goneType.getId());

    byte[] backup = backupService.createBackup();
    wipe();
    backupService.restoreFromBackup(backup);

    ParticipantDTO restoredActive = participantService.findByUuid(active.getParticipantUuid());
    assertNotNull(restoredActive, "uuids are kept, so the old links still resolve");
    assertEquals("Active edited", restoredActive.getName());
    assertEquals(0L, restoredActive.getVersion(), "restored rows start again at 0");
    assertNull(participantService.findByUuid(gone.getParticipantUuid()), "still deleted");
    List<DeletedRecordDTO> deletedParticipants = trash.list(DeletedKind.PARTICIPANTS, FIRST_PAGE).getContent();
    assertEquals(1, deletedParticipants.size());
    assertEquals(gone.getParticipantUuid().toString(), deletedParticipants.getFirst().key());
    assertEquals(SoftDeleteSupport.SYSTEM_USER, deletedParticipants.getFirst().deletedBy());
    assertEquals(1, trash.list(DeletedKind.COURSE_TYPES, FIRST_PAGE).getTotalElements());
    assertEquals(1, trash.list(DeletedKind.COURSES, FIRST_PAGE).getTotalElements());
    assertEquals(1, coursesService.findAll().size(), "one active course, one in the trash");
    CoursesDTO restoredCourse = coursesService.findAll().getFirst();
    assertEquals(active.getParticipantUuid(), restoredCourse.getParticipantUuid());
    assertEquals(Set.of(trainer.getId()), restoredCourse.getTrainerIds());
    assertEquals(counter.uuid(), restoredCourse.getCourseCounterUuid());
    assertEquals(photo.getId(), trainerService.findById(trainer.getId()).getImagesUuid().iterator().next());
    assertEquals(1L, count("select count(*) from image"), "a shared photo is one row, before and after");
    assertEquals("T-1", courseTypeService.findAll().getFirst().getCode());
    assertFalse(courseTypeService.findAll().stream().anyMatch(ct -> ct.getCode().equals("T-GONE")));
  }

  @Test
  void aBackupWrittenBeforeSoftDeleteStillReadsAsActiveRows() throws Exception {
    ParticipantDTO saved = participantService.save(participant("Old"));
    participantService.deleteByUuid(saved.getParticipantUuid());
    String json = gunzip(backupService.createBackup())
        .replace("\"schemaVersion\":\"" + DatabaseBackupService.SCHEMA_VERSION + "\"", "\"schemaVersion\":\"1.0\"")
        .replaceAll(",\"deletedAt\":\"[^\"]*\"", "")
        .replaceAll(",\"deletedBy\":\"[^\"]*\"", "");
    assertFalse(json.contains("deletedAt"), "the 1.0 shape has no deletion state");
    wipe();

    backupService.restoreFromBackup(gzip(json));

    assertNotNull(participantService.findByUuid(saved.getParticipantUuid()), "a 1.0 row is an active row");
    assertEquals(0, trash.list(DeletedKind.PARTICIPANTS, FIRST_PAGE).getTotalElements());
  }

  @Test
  void theBackupCarriesDeletionStateButNoVersion() throws Exception {
    ParticipantDTO saved = participantService.save(participant("Marked"));
    participantService.deleteByUuid(saved.getParticipantUuid());

    String json = gunzip(backupService.createBackup());

    assertTrue(json.contains("\"deletedBy\":\"" + SoftDeleteSupport.SYSTEM_USER + "\""), json);
    assertTrue(json.contains("\"deletedAt\":\""), json);
    assertFalse(json.contains("\"version\""), "restored rows start again at 0");
  }

  // ---- helpers ----------------------------------------------------------------------------------------

  private void wipe() {
    tx.executeWithoutResult(s -> TABLES.forEach(t -> em.createNativeQuery("DELETE FROM " + t).executeUpdate()));
    assertEquals(0L, count("select count(*) from participants"));
  }

  private long count(String sql) {
    return tx.execute(s -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
  }

  private static String gunzip(byte[] data) throws Exception {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static byte[] gzip(String json) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
      out.write(json.getBytes(StandardCharsets.UTF_8));
    }
    return bytes.toByteArray();
  }

  private static ParticipantDTO participant(String name) {
    ParticipantDTO dto = new ParticipantDTO();
    dto.setName(name);
    dto.setSurname("Participant");
    dto.setBirthDate(LocalDate.of(1990, 1, 1));
    return dto;
  }

  private static CourseTypeDTO courseType(String code) {
    CourseTypeDTO dto = new CourseTypeDTO();
    dto.setCode(code);
    dto.setDescription("Description " + code);
    return dto;
  }

  private static TrainerDTO trainer(String name, Set<java.util.UUID> images) {
    TrainerDTO dto = new TrainerDTO();
    dto.setName(name);
    dto.setSurname("Trainer");
    dto.setImagesUuid(images);
    return dto;
  }

  private static CoursesDTO course(ParticipantDTO participant, CourseTypeDTO type) {
    CoursesDTO dto = new CoursesDTO();
    dto.setParticipantUuid(participant.getParticipantUuid());
    dto.setCourseTypeId(type.getId());
    dto.setStartDate(LocalDate.of(2026, 1, 1));
    dto.setEndDate(LocalDate.of(2026, 1, 10));
    return dto;
  }
}
