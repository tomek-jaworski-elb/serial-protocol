package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optimistic locking on the real entities. Each step commits in its own transaction, because the stale-form
 * check happens at commit for one of the cases (a parent deleted while a course is being saved).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SoftDeleteSupport.class, DeletedRecordsService.class, ParticipantService.class, CourseTypeService.class,
    CourseCounterService.class, CoursesService.class, TrainerService.class, ImageService.class})
class OptimisticLockingTest {

  private static final List<String> TABLES = List.of(
      "courses_trainers", "courses_lecturers", "courses_technicians", "courses", "participants",
      "trainer_image", "lecturer_image", "technician_image", "trainer", "lecturer", "technician",
      "course_counter", "course_type", "image");

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
  private DeletedRecordsService trash;
  @Autowired
  private SoftDeleteSupport support;
  @Autowired
  private ParticipantRepository participantRepository;
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

  // ---- a stale form is rejected on every kind of update path -------------------------------------------

  @Test
  void staleEditOfACourseTypeIsRejected() {
    CourseTypeDTO opened = courseTypeService.save(courseType("T-1"));
    assertEquals(0L, opened.getVersion(), "a new record starts at version 0");

    CourseTypeDTO byOther = courseTypeService.findById(opened.getId());
    byOther.setDescription("changed by someone else");
    assertEquals(1L, courseTypeService.update(byOther).getVersion(), "every save bumps the version");

    opened.setDescription("my stale edit");
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> courseTypeService.update(opened));
    assertEquals("changed by someone else", courseTypeService.findById(opened.getId()).getDescription());
  }

  @Test
  void staleEditOfAParticipantIsRejected() {
    ParticipantDTO opened = participantService.save(participant("Jan"));
    ParticipantDTO byOther = participantService.findByUuid(opened.getParticipantUuid());
    byOther.setName("Janusz");
    participantService.updateByUuid(byOther);

    opened.setName("Jan-stale");
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> participantService.updateByUuid(opened));
    assertEquals("Janusz", participantService.findByUuid(opened.getParticipantUuid()).getName());
  }

  @Test
  void staleEditOfATrainerIsRejected() {
    TrainerDTO opened = trainerService.save(trainer("Tom"));
    TrainerDTO byOther = trainerService.findById(opened.getId());
    byOther.setName("Thomas");
    trainerService.update(byOther);

    opened.setName("Tom-stale");
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> trainerService.update(opened));
    assertEquals("Thomas", trainerService.findById(opened.getId()).getName());
  }

  @Test
  void staleEditOfACourseCounterIsRejected() {
    CourseCounterDTO opened = courseCounterService.save(new CourseCounterDTO(5L, null));
    courseCounterService.update(new CourseCounterDTO(opened.uuid(), 6L, null, opened.version()));

    assertThrows(ObjectOptimisticLockingFailureException.class,
        () -> courseCounterService.update(new CourseCounterDTO(opened.uuid(), 7L, null, opened.version())));
    assertEquals(6L, courseCounterService.getByUuid(opened.uuid()).orElseThrow().counter());
  }

  @Test
  void staleEditOfACourseIsRejected() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO opened = coursesService.save(course(participant, type));
    CoursesDTO byOther = coursesService.findByUuid(opened.getUuid());
    byOther.setEndDate(LocalDate.of(2026, 2, 1));
    coursesService.update(byOther);

    opened.setEndDate(LocalDate.of(2026, 3, 1));
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> coursesService.update(opened));
    assertEquals(LocalDate.of(2026, 2, 1), coursesService.findByUuid(opened.getUuid()).getEndDate());
  }

  @Test
  void aFormWithoutAVersionIsRejectedLoudly() {
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    type.setVersion(null);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> courseTypeService.update(type));
    assertTrue(error.getMessage().contains("version"), error.getMessage());

    TrainerDTO trainer = trainerService.save(trainer("Tom"));
    trainer.setVersion(null);
    assertThrows(IllegalArgumentException.class, () -> trainerService.update(trainer));
  }

  // ---- delete, restore and the parents of a course -----------------------------------------------------

  @Test
  void deleteAndRestoreBumpTheVersionWithoutCheckingIt() {
    ParticipantDTO saved = participantService.save(participant("Jan"));
    participantService.deleteByUuid(saved.getParticipantUuid());
    Participant hidden = tx.execute(s -> support.withDeleted(
        () -> participantRepository.findById(saved.getParticipantUuid()).orElseThrow()));
    assertEquals(1L, hidden.getVersion(), "deleting is a write");

    trash.restore(DeletedKind.PARTICIPANTS, saved.getParticipantUuid().toString());

    assertEquals(2L, participantService.findByUuid(saved.getParticipantUuid()).getVersion(), "so is restoring");
  }

  @Test
  void savingACourseWhoseParticipantIsDeletedMeanwhileIsRejected() throws Exception {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));

    assertThrows(ObjectOptimisticLockingFailureException.class, () -> tx.executeWithoutResult(s -> {
      coursesService.save(course(participant, type));   // the parents are locked optimistically here
      inAnotherTransaction(() -> participantService.deleteByUuid(participant.getParticipantUuid()));
    }), "the commit must notice that a locked parent moved on");

    assertTrue(coursesService.findAll().isEmpty(), "nothing was saved");
  }

  /** Runs the work on another thread, so it gets a transaction of its own and commits before we return. */
  private static void inAnotherTransaction(Runnable work) {
    try {
      CompletableFuture.runAsync(work).get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new IllegalStateException("the other transaction failed", e.getCause());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- helpers ----------------------------------------------------------------------------------------

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

  private static TrainerDTO trainer(String name) {
    TrainerDTO dto = new TrainerDTO();
    dto.setName(name);
    dto.setSurname("Trainer");
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
