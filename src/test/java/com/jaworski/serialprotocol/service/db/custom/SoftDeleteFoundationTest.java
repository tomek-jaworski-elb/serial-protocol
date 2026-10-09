package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.repository.custom.CourseCounterRepository;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import com.jaworski.serialprotocol.service.db.DatabaseBackupService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Soft-delete foundation, checked against the seven real entities (not the Phase 0 stand-ins).
 *
 * <p>Every step runs in its own transaction, so each gets a fresh session like a request with
 * open-in-view off. That matters: inside one session {@code findById} would still return an entity
 * that has just been marked deleted.</p>
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SoftDeleteSupport.class, DatabaseBackupService.class, SoftDeleteFoundationTest.JsonConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoftDeleteFoundationTest {

  @TestConfiguration
  static class JsonConfig {
    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder()
          .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          // restore reads one element at a time from a larger document, as Spring Boot's mapper allows
          .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
          .build();
    }
  }

  /** Children before parents, join tables first. */
  private static final List<String> TABLES = List.of(
      "courses_trainers", "courses_lecturers", "courses_technicians", "courses", "participants",
      "trainer_image", "lecturer_image", "technician_image", "trainer", "lecturer", "technician",
      "course_counter", "course_type", "image");

  @Autowired
  private SoftDeleteSupport support;
  @Autowired
  private DatabaseBackupService backupService;
  @Autowired
  private PlatformTransactionManager txManager;
  @Autowired
  private CourseTypeRepository courseTypeRepository;
  @Autowired
  private CourseCounterRepository courseCounterRepository;
  @Autowired
  private TrainerRepository trainerRepository;
  @Autowired
  private LecturerRepository lecturerRepository;
  @Autowired
  private TechnicianRepository technicianRepository;
  @Autowired
  private ParticipantRepository participantRepository;
  @Autowired
  private CoursesRepository coursesRepository;
  @PersistenceContext
  private EntityManager em;

  private TransactionTemplate tx;

  @BeforeEach
  void clean() {
    tx = new TransactionTemplate(txManager);
    tx.executeWithoutResult(s -> TABLES.forEach(t -> em.createNativeQuery("DELETE FROM " + t).executeUpdate()));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ---- the seven entities -----------------------------------------------------------------------

  private record Kind<T extends SoftDeletable, ID>(String table, Supplier<T> create,
                                                   JpaRepository<T, ID> repo, Function<T, ID> idOf) {
    @Override
    public String toString() {
      return table;
    }
  }

  private static Trainer trainer(String name) {
    Trainer t = new Trainer();
    t.setName(name);
    t.setSurname("Surname");
    return t;
  }

  private Participant participant(long id) {
    Participant p = new Participant();
    p.setId(id);
    p.setName("Pat");
    p.setSurname("Participant");
    return participantRepository.save(p);
  }

  private CourseType courseType(String code) {
    return courseTypeRepository.save(new CourseType(code, "description", null));
  }

  private Courses course() {
    Courses c = new Courses();
    c.setId(1L);
    c.setParticipant(participant(1L));
    c.setCourseType(courseType("C-1"));
    c.setStartDate(LocalDate.of(2026, 1, 1));
    c.setEndDate(LocalDate.of(2026, 1, 2));
    return c;
  }

  Stream<Kind<?, ?>> kinds() {
    return Stream.of(
        new Kind<>(Participant.TABLE_NAME, this::newParticipantEntity, participantRepository, Participant::getUuid),
        new Kind<>(Lecturer.TABLE_NAME, () -> {
          Lecturer l = new Lecturer();
          l.setName("L");
          l.setSurname("S");
          return l;
        }, lecturerRepository, Lecturer::getUuid),
        new Kind<>(Trainer.TABLE_NAME, () -> trainer("T"), trainerRepository, Trainer::getUuid),
        new Kind<>(Technician.TABLE_NAME, () -> {
          Technician t = new Technician();
          t.setName("T");
          t.setSurname("S");
          return t;
        }, technicianRepository, Technician::getUuid),
        new Kind<>(CourseType.TABLE_NAME, () -> new CourseType("CT", "description", null),
            courseTypeRepository, CourseType::getId),
        new Kind<>(CourseCounter.TABLE_NAME, () -> {
          CourseCounter cc = new CourseCounter();
          cc.setCounter(5L);
          return cc;
        }, courseCounterRepository, CourseCounter::getUuid),
        new Kind<>(Courses.TABLE_NAME, this::course, coursesRepository, Courses::getUuid));
  }

  private Participant newParticipantEntity() {
    Participant p = new Participant();
    p.setId(70L);
    p.setName("P");
    p.setSurname("S");
    return p;
  }

  // ---- behaviour, once per entity -------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("kinds")
  void markedRowIsHiddenRestorableAndSurvivesAStrayDelete(Kind<?, ?> kind) {
    roundTrip(kind);
  }

  private <T extends SoftDeletable, ID> void roundTrip(Kind<T, ID> kind) {
    JpaRepository<T, ID> repo = kind.repo();
    ID id = kind.idOf().apply(tx.execute(s -> repo.saveAndFlush(kind.create().get())));
    assertNotNull(id);

    // marking must not change the hash: entities live in Sets
    tx.executeWithoutResult(s -> {
      T entity = repo.findById(id).orElseThrow();
      int hash = entity.hashCode();
      support.markDeleted(entity);
      repo.saveAndFlush(entity);
      assertEquals(hash, entity.hashCode(), "deleted_* must stay out of hashCode");
    });

    assertTrue(tx.execute(s -> repo.findById(id)).isEmpty(), "findById must not see it");
    assertTrue(tx.execute(s -> repo.findAll()).isEmpty(), "findAll must not see it");
    Boolean exists = tx.execute(s -> repo.existsById(id));
    assertFalse(exists, "existsById must not see it");
    assertEquals(1L, count("SELECT COUNT(*) FROM " + kind.table()), "the row is still in the table");

    T seen = tx.execute(s -> support.withDeleted(() -> repo.findById(id))).orElseThrow();
    assertNotNull(seen.getDeletedAt());
    assertEquals(SoftDeleteSupport.SYSTEM_USER, seen.getDeletedBy());
    assertTrue(seen.isDeleted());

    assertTrue(tx.execute(s -> {
      support.withDeleted(() -> repo.findAll());
      return repo.findAll();
    }).isEmpty(), "the filter is back on once withDeleted returns");

    tx.executeWithoutResult(s -> support.runWithDeleted(() -> {
      T entity = repo.findById(id).orElseThrow();
      support.restore(entity);
      repo.saveAndFlush(entity);
    }));
    T restored = tx.execute(s -> repo.findById(id)).orElseThrow();
    assertNull(restored.getDeletedAt());
    assertNull(restored.getDeletedBy());

    // a stray repository.delete() must only mark the row (the @SQLDelete safety net)
    tx.executeWithoutResult(s -> repo.deleteById(id));
    assertEquals(1L, count("SELECT COUNT(*) FROM " + kind.table()), "stray delete kept the row");
    assertEquals(1L, count("SELECT COUNT(*) FROM " + kind.table()
        + " WHERE deleted_at IS NOT NULL AND deleted_by = 'system'"), "a stray delete records 'system'");
    assertTrue(tx.execute(s -> repo.findById(id)).isEmpty());
  }

  // ---- support -------------------------------------------------------------------------------

  @Test
  void nestedWithDeletedKeepsTheFilterOffUntilTheOuterOneReturns() {
    UUID id = tx.execute(s -> trainerRepository.saveAndFlush(trainer("nested")).getUuid());
    tx.executeWithoutResult(s -> {
      Trainer t = trainerRepository.findById(id).orElseThrow();
      support.markDeleted(t);
      trainerRepository.saveAndFlush(t);
    });

    int seenAfterInner = tx.execute(s -> support.withDeleted(() -> {
      support.withDeleted(() -> trainerRepository.findAll());
      return trainerRepository.findAll().size();
    }));
    assertEquals(1, seenAfterInner, "the inner call must not switch the filter back on");

    assertTrue(tx.execute(s -> trainerRepository.findAll()).isEmpty(), "and the outer one does");
  }

  @Test
  void withDeletedOutsideATransactionIsRejected() {
    assertThrows(IllegalStateException.class, () -> support.withDeleted(() -> "nope"));
  }

  @Test
  void currentUserFollowsTheSecurityContext() {
    assertEquals(SoftDeleteSupport.SYSTEM_USER, support.currentUser(), "nobody signed in");

    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", "x", AuthorityUtils.NO_AUTHORITIES));
    assertEquals("alice", support.currentUser());

    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("x".repeat(150), "x", AuthorityUtils.NO_AUTHORITIES));
    assertEquals(100, support.currentUser().length(), "fits the deleted_by column");

    SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    assertEquals(SoftDeleteSupport.SYSTEM_USER, support.currentUser(), "anonymous is not a person");
  }

  @Test
  void markDeletedRecordsTheSignedInUser() {
    UUID id = tx.execute(s -> trainerRepository.saveAndFlush(trainer("who")).getUuid());
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", "x", AuthorityUtils.NO_AUTHORITIES));

    tx.executeWithoutResult(s -> {
      Trainer t = trainerRepository.findById(id).orElseThrow();
      support.markDeleted(t);
      trainerRepository.saveAndFlush(t);
    });

    Trainer seen = tx.execute(s -> support.withDeleted(() -> trainerRepository.findById(id))).orElseThrow();
    assertEquals("alice", seen.getDeletedBy());
  }

  // ---- what the guards and the backup see ---------------------------------------------------------------

  @Test
  void aDeletedCourseNoLongerBlocksItsParticipant() {
    Courses saved = tx.execute(s -> coursesRepository.saveAndFlush(course()));
    UUID participantUuid = saved.getParticipant().getUuid();
    Boolean blockedBefore = tx.execute(s -> coursesRepository.existsByParticipant_Uuid(participantUuid));
    assertTrue(blockedBefore);

    tx.executeWithoutResult(s -> {
      Courses c = coursesRepository.findById(saved.getUuid()).orElseThrow();
      support.markDeleted(c);
      coursesRepository.saveAndFlush(c);
    });

    Boolean blockedAfter = tx.execute(s -> coursesRepository.existsByParticipant_Uuid(participantUuid));
    assertFalse(blockedAfter, "the guard counts active courses only");
  }

  /** Clearing the tables before a restore must remove marked rows too; the backup then brings them back marked. */
  @Test
  void restoringABackupReplacesSoftDeletedRowsInsteadOfLeavingThemBehind() throws Exception {
    UUID doomed = tx.execute(s -> trainerRepository.saveAndFlush(trainer("doomed")).getUuid());
    tx.executeWithoutResult(s -> {
      Trainer t = trainerRepository.findById(doomed).orElseThrow();
      support.markDeleted(t);
      trainerRepository.saveAndFlush(t);
    });
    byte[] backup = backupService.createBackup();
    tx.executeWithoutResult(s -> {
      Trainer t = trainerRepository.saveAndFlush(trainer("marked after the backup"));
      support.markDeleted(t);
      trainerRepository.saveAndFlush(t);
    });
    assertEquals(2L, count("SELECT COUNT(*) FROM trainer"));

    backupService.restoreFromBackup(backup);

    assertEquals(1L, count("SELECT COUNT(*) FROM trainer"), "the row marked after the backup is gone");
    assertEquals(1L, count("SELECT COUNT(*) FROM trainer WHERE deleted_at IS NOT NULL"), "the backed-up one is back, still deleted");
    assertTrue(tx.execute(s -> trainerRepository.findById(doomed)).isEmpty());
  }

  private long count(String sql) {
    return tx.execute(s -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
  }
}
