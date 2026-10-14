package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.DeletedRecordDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The administrator's trash on the real entities: listing, restore rules, purge rules and photo cleanup. */
@DataJpaTest
@Import({SoftDeleteSupport.class, DeletedRecordsService.class, ParticipantService.class, CourseTypeService.class,
    CourseCounterService.class, CoursesService.class, TrainerService.class, ImageService.class})
class DeletedRecordsServiceTest {

  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

  @Autowired
  private DeletedRecordsService trash;
  @Autowired
  private ParticipantService participantService;
  @Autowired
  private CourseTypeService courseTypeService;
  @Autowired
  private CoursesService coursesService;
  @Autowired
  private TrainerService trainerService;
  @Autowired
  private ImageService imageService;
  @PersistenceContext
  private EntityManager em;

  // ---- listing -----------------------------------------------------------------------------------

  @Test
  void listShowsOnlyTheDeletedRowsOfThatKind() {
    ParticipantDTO kept = participantService.save(participant("Kept"));
    ParticipantDTO gone = participantService.save(participant("Gone"));
    participantService.deleteByUuid(gone.getParticipantUuid());

    Page<DeletedRecordDTO> rows = trash.list(DeletedKind.PARTICIPANTS, FIRST_PAGE);

    assertEquals(1, rows.getTotalElements());
    DeletedRecordDTO row = rows.getContent().getFirst();
    assertEquals(gone.getParticipantUuid().toString(), row.key());
    assertEquals("Participant Gone", row.title());
    assertTrue(row.details().startsWith("No. " + gone.getId()), row.details());
    assertEquals(SoftDeleteSupport.SYSTEM_USER, row.deletedBy());
    assertTrue(row.deletedAt().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"), row.deletedAt());
    assertEquals(0, trash.list(DeletedKind.TRAINERS, FIRST_PAGE).getTotalElements());
    assertNotNull(participantService.findByUuid(kept.getParticipantUuid()));
  }

  @Test
  void aDeletedCourseIsListedEvenWhenItsParticipantIsDeletedToo() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = coursesService.save(course(participant, type));
    coursesService.deleteByUuid(course.getUuid());
    participantService.deleteByUuid(participant.getParticipantUuid());

    List<DeletedRecordDTO> rows = trash.list(DeletedKind.COURSES, FIRST_PAGE).getContent();

    assertEquals(1, rows.size());
    assertEquals("Course no. " + course.getId(), rows.getFirst().title());
    assertTrue(rows.getFirst().details().contains("Participant Jan"), rows.getFirst().details());
    assertTrue(rows.getFirst().details().contains("T-1"), rows.getFirst().details());
  }

  // ---- restore -----------------------------------------------------------------------------------

  @Test
  void restoreMakesTheRecordVisibleAgain() {
    ParticipantDTO saved = participantService.save(participant("Jan"));
    participantService.deleteByUuid(saved.getParticipantUuid());
    assertNull(participantService.findByUuid(saved.getParticipantUuid()));

    trash.restore(DeletedKind.PARTICIPANTS, saved.getParticipantUuid().toString());

    assertNotNull(participantService.findByUuid(saved.getParticipantUuid()));
    assertEquals(0, trash.list(DeletedKind.PARTICIPANTS, FIRST_PAGE).getTotalElements());
  }

  @Test
  void restoringACourseWaitsForItsDeletedParents() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = coursesService.save(course(participant, type));
    coursesService.deleteByUuid(course.getUuid());
    participantService.deleteByUuid(participant.getParticipantUuid());
    courseTypeService.deleteById(type.getId());

    IllegalStateException blocked = assertThrows(IllegalStateException.class,
        () -> trash.restore(DeletedKind.COURSES, course.getUuid().toString()));
    assertTrue(blocked.getMessage().contains("participant Participant Jan"), blocked.getMessage());
    assertTrue(blocked.getMessage().contains("course type T-1"), blocked.getMessage());
    assertNull(coursesService.findByUuid(course.getUuid()), "a blocked restore changes nothing");

    trash.restore(DeletedKind.PARTICIPANTS, participant.getParticipantUuid().toString());
    trash.restore(DeletedKind.COURSE_TYPES, String.valueOf(type.getId()));
    trash.restore(DeletedKind.COURSES, course.getUuid().toString());

    assertNotNull(coursesService.findByUuid(course.getUuid()));
  }

  @Test
  void onlyDeletedRecordsCanBeRestoredOrPurged() {
    ParticipantDTO active = participantService.save(participant("Jan"));
    String key = active.getParticipantUuid().toString();

    assertThrows(IllegalArgumentException.class, () -> trash.restore(DeletedKind.PARTICIPANTS, key));
    assertThrows(IllegalArgumentException.class, () -> trash.purge(DeletedKind.PARTICIPANTS, key));
    assertThrows(IllegalArgumentException.class,
        () -> trash.restore(DeletedKind.PARTICIPANTS, UUID.randomUUID().toString()));
    assertThrows(IllegalArgumentException.class, () -> trash.restore(DeletedKind.PARTICIPANTS, "not-a-uuid"));
    assertThrows(IllegalArgumentException.class, () -> trash.restore(DeletedKind.COURSE_TYPES, "abc"));
    assertNotNull(participantService.findByUuid(active.getParticipantUuid()));
  }

  // ---- purge -------------------------------------------------------------------------------------

  @Test
  void purgingWaitsForEveryCourseThatPointsAtTheRecordDeletedOnesIncluded() {
    Image photo = imageService.saveImage(new byte[]{1, 2, 3}, "image/png");
    ParticipantDTO participant = participant("Jan");
    participant.setImage(photo.getId());
    participant = participantService.save(participant);
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = coursesService.save(course(participant, type));
    coursesService.deleteByUuid(course.getUuid());
    participantService.deleteByUuid(participant.getParticipantUuid());
    String participantKey = participant.getParticipantUuid().toString();

    IllegalStateException blocked = assertThrows(IllegalStateException.class,
        () -> trash.purge(DeletedKind.PARTICIPANTS, participantKey));
    assertTrue(blocked.getMessage().contains("course"), blocked.getMessage());
    assertEquals(1, count("select count(*) from participants"), "a blocked purge changes nothing");
    assertNull(participantService.findByUuid(participant.getParticipantUuid()),
        "a refused purge leaves no hidden row behind in the session");

    trash.purge(DeletedKind.COURSES, course.getUuid().toString());
    trash.purge(DeletedKind.PARTICIPANTS, participantKey);

    assertEquals(0, count("select count(*) from courses"));
    assertEquals(0, count("select count(*) from participants"));
    assertNull(imageService.getImageById(photo.getId()), "the participant's photo had no other owner");
    assertNotNull(courseTypeService.findById(type.getId()), "the course type was only referenced, not owned");
  }

  @Test
  void purgingATrainerDropsItsLinksAndOnlyThePhotosNobodyElseUses() {
    Image shared = imageService.saveImage(new byte[]{1}, "image/png");
    Image own = imageService.saveImage(new byte[]{2}, "image/png");
    TrainerDTO doomed = trainerService.save(trainer("Doomed", Set.of(shared.getId(), own.getId())));
    TrainerDTO other = trainerService.save(trainer("Other", Set.of(shared.getId())));
    trainerService.deleteById(doomed.getId());
    assertEquals(3, count("select count(*) from trainer_image"));

    trash.purge(DeletedKind.TRAINERS, doomed.getId().toString());

    assertEquals(1, count("select count(*) from trainer"));
    assertEquals(1, count("select count(*) from trainer_image"), "only the other trainer's link remains");
    assertNotNull(trainerService.findById(other.getId()));
    assertNotNull(imageService.getImageById(shared.getId()), "still used by the other trainer");
    assertNull(imageService.getImageById(own.getId()), "nobody used it any more");
  }

  @Test
  void purgingACourseDropsItsStaffLinksButNotTheStaff() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    TrainerDTO trainer = trainerService.save(trainer("Tom", Set.of()));
    CoursesDTO course = course(participant, type);
    course.setTrainerIds(Set.of(trainer.getId()));
    course = coursesService.save(course);
    assertEquals(1, count("select count(*) from courses_trainers"));
    coursesService.deleteByUuid(course.getUuid());

    trash.purge(DeletedKind.COURSES, course.getUuid().toString());

    assertEquals(0, count("select count(*) from courses"));
    assertEquals(0, count("select count(*) from courses_trainers"));
    assertNotNull(trainerService.findById(trainer.getId()));
    assertNotNull(participantService.findByUuid(participant.getParticipantUuid()));
  }

  @Test
  void aPurgedTrainerCanNoLongerBlockAnything() {
    TrainerDTO trainer = trainerService.save(trainer("Tom", Set.of()));
    trainerService.deleteById(trainer.getId());
    trash.purge(DeletedKind.TRAINERS, trainer.getId().toString());

    assertEquals(0, trash.list(DeletedKind.TRAINERS, FIRST_PAGE).getTotalElements());
    assertThrows(IllegalArgumentException.class,
        () -> trash.restore(DeletedKind.TRAINERS, trainer.getId().toString()));
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private long count(String sql) {
    em.flush();
    return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
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

  private static TrainerDTO trainer(String name, Set<UUID> images) {
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
