package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a user's DELETE now does through the services, and what that means for ids, unique values and
 * the courses that point at the deleted record. Everything runs in one test transaction; that works
 * because {@code SoftDeleteSupport#softDelete} drops the entity from the session.
 */
@DataJpaTest
@Import({SoftDeleteSupport.class, ParticipantService.class, CourseTypeService.class, CourseCounterService.class,
    CoursesService.class, TrainerService.class, ImageService.class})
class SoftDeleteUserPathTest {

  @Autowired
  private SoftDeleteSupport support;
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
  private ParticipantRepository participantRepository;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ---- deleting ----------------------------------------------------------------------------------

  @Test
  void deleteHidesTheRecordAndRemembersWhoDidIt() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", "x", AuthorityUtils.NO_AUTHORITIES));
    ParticipantDTO saved = participantService.save(participant("Jan"));

    participantService.deleteByUuid(saved.getParticipantUuid());

    assertNull(participantService.findByUuid(saved.getParticipantUuid()));
    assertTrue(participantService.findAll().isEmpty());
    Participant hidden = support.withDeleted(() -> participantRepository.findById(saved.getParticipantUuid()))
        .orElseThrow();
    assertEquals("alice", hidden.getDeletedBy());
    assertNotNull(hidden.getDeletedAt());
  }

  @Test
  void deletingATrainerKeepsItsPhotos() {
    Image photo = imageService.saveImage(new byte[]{1, 2, 3}, "image/png");
    TrainerDTO trainer = new TrainerDTO();
    trainer.setName("Tom");
    trainer.setSurname("Trainer");
    trainer.setImagesUuid(Set.of(photo.getId()));
    TrainerDTO saved = trainerService.save(trainer);

    trainerService.deleteById(saved.getId());

    assertNull(trainerService.findById(saved.getId()));
    assertNotNull(imageService.getImageById(photo.getId()), "the photo is needed if the trainer is restored");
  }

  @Test
  void deletingAnAlreadyDeletedRecordIsHarmless() {
    ParticipantDTO saved = participantService.save(participant("Jan"));
    participantService.deleteByUuid(saved.getParticipantUuid());

    participantService.deleteByUuid(saved.getParticipantUuid());

    assertNull(participantService.findByUuid(saved.getParticipantUuid()));
  }

  @Test
  void editingADeletedRecordThroughAnOldFormIsAPlainNotFound() {
    ParticipantDTO saved = participantService.save(participant("Jan"));
    participantService.deleteByUuid(saved.getParticipantUuid());
    saved.setName("edited after the delete");

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> participantService.updateByUuid(saved));

    assertTrue(error.getMessage().contains("not found"), error.getMessage());
  }

  @Test
  void aDeletedCourseNoLongerBlocksDeletingItsParticipant() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = coursesService.save(course(participant, type));
    assertThrows(IllegalStateException.class,
        () -> participantService.deleteByUuid(participant.getParticipantUuid()));

    coursesService.deleteByUuid(course.getUuid());
    participantService.deleteByUuid(participant.getParticipantUuid());

    assertNull(participantService.findByUuid(participant.getParticipantUuid()));
  }

  // ---- ids are never handed out again --------------------------------------------------------------

  @Test
  void nextIdsSkipDeletedRecords() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = coursesService.save(course(participant, type));
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(7L, null));
    Long nextParticipant = participantService.nextId();
    Long nextCourse = coursesService.nextId();
    Long nextCounter = courseCounterService.nextCounter();

    coursesService.deleteByUuid(course.getUuid());
    participantService.deleteByUuid(participant.getParticipantUuid());
    courseCounterService.delete(counter.uuid());

    assertEquals(nextParticipant, participantService.nextId(), "a deleted participant's id stays taken");
    assertEquals(nextCourse, coursesService.nextId(), "a deleted course's id stays taken");
    assertEquals(nextCounter, courseCounterService.nextCounter(), "a deleted counter stays taken");
  }

  // ---- unique values held by deleted records ---------------------------------------------------------

  @Test
  void aDeletedCourseTypeKeepsItsCode() {
    CourseTypeDTO saved = courseTypeService.save(courseType("T-1"));
    courseTypeService.deleteById(saved.getId());

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> courseTypeService.save(courseType("T-1")));

    assertTrue(error.getMessage().contains("used by a deleted record"), error.getMessage());
    assertTrue(error.getMessage().contains("T-1"));
  }

  @Test
  void anActiveDuplicateIsStillReportedAsAnActiveDuplicate() {
    courseTypeService.save(courseType("T-1"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> courseTypeService.save(courseType("T-1")));

    assertTrue(error.getMessage().contains("already exists"), error.getMessage());
  }

  @Test
  void renamingACourseTypeToADeletedCodeIsRefused() {
    CourseTypeDTO gone = courseTypeService.save(courseType("OLD"));
    courseTypeService.deleteById(gone.getId());
    CourseTypeDTO kept = courseTypeService.save(courseType("KEPT"));
    kept.setCode("OLD");

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> courseTypeService.update(kept));

    assertTrue(error.getMessage().contains("used by a deleted record"), error.getMessage());
  }

  @Test
  void aDeletedParticipantKeepsItsNumber() {
    ParticipantDTO saved = participantService.save(participant("Jan"));
    participantService.deleteByUuid(saved.getParticipantUuid());
    ParticipantDTO again = participant("Anna");
    again.setId(saved.getId());

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> participantService.save(again));

    assertTrue(error.getMessage().contains("used by a deleted record"), error.getMessage());
  }

  @Test
  void aDeletedCounterKeepsItsValueAndActiveDuplicatesAreReported() {
    CourseCounterDTO active = courseCounterService.save(new CourseCounterDTO(5L, null));
    IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
        () -> courseCounterService.save(new CourseCounterDTO(5L, null)));
    assertTrue(duplicate.getMessage().contains("already exists"), duplicate.getMessage());

    courseCounterService.delete(active.uuid());
    IllegalArgumentException held = assertThrows(IllegalArgumentException.class,
        () -> courseCounterService.save(new CourseCounterDTO(5L, null)));
    assertTrue(held.getMessage().contains("used by a deleted record"), held.getMessage());
  }

  @Test
  void editingACounterToItsOwnValueIsNotADuplicate() {
    CourseCounterDTO saved = courseCounterService.save(new CourseCounterDTO(5L, null));

    CourseCounterDTO updated = courseCounterService.update(new CourseCounterDTO(saved.uuid(), 5L, null, saved.version()));

    assertEquals(5L, updated.counter());
  }

  // ---- courses may only point at active records ----------------------------------------------------------

  @Test
  void aCourseCannotBeSavedForADeletedParticipant() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    participantService.deleteByUuid(participant.getParticipantUuid());

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> coursesService.save(course(participant, type)));

    assertTrue(error.getMessage().contains("participant"), error.getMessage());
    assertTrue(error.getMessage().contains("does not exist or has been deleted"), error.getMessage());
  }

  @Test
  void aCourseCannotBeSavedForADeletedTypeTrainerOrCounter() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    TrainerDTO trainer = trainerService.save(trainer("Tom"));
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(9L, null));

    trainerService.deleteById(trainer.getId());
    CoursesDTO withTrainer = course(participant, type);
    withTrainer.setTrainerIds(Set.of(trainer.getId()));
    assertTrue(assertThrows(IllegalArgumentException.class, () -> coursesService.save(withTrainer))
        .getMessage().contains("trainers"));

    courseCounterService.delete(counter.uuid());
    CoursesDTO withCounter = course(participant, type);
    withCounter.setCourseCounterUuid(counter.uuid());
    assertTrue(assertThrows(IllegalArgumentException.class, () -> coursesService.save(withCounter))
        .getMessage().contains("course counter"));

    courseTypeService.deleteById(type.getId());
    assertTrue(assertThrows(IllegalArgumentException.class, () -> coursesService.save(course(participant, type)))
        .getMessage().contains("course type"));
  }

  @Test
  void aCourseNamesACounterInTheTrashWhenAskedForItByNumber() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(9L, null));
    courseCounterService.delete(counter.uuid());

    CoursesDTO byNumber = course(participant, type);
    byNumber.setCounter(9L);
    IllegalArgumentException deleted = assertThrows(IllegalArgumentException.class, () -> coursesService.save(byNumber));
    assertTrue(deleted.getMessage().contains("has been deleted"), deleted.getMessage());

    byNumber.setCounter(10L);
    IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> coursesService.save(byNumber));
    assertTrue(missing.getMessage().contains("does not exist"), missing.getMessage());
  }

  @Test
  void aCourseCannotPointAtSomethingThatNeverExisted() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO course = course(participant, type);
    course.setLecturerIds(Set.of(UUID.randomUUID()));

    assertThrows(IllegalArgumentException.class, () -> coursesService.save(course));
  }

  @Test
  void editingACourseChecksItsParentsToo() {
    ParticipantDTO participant = participantService.save(participant("Jan"));
    CourseTypeDTO type = courseTypeService.save(courseType("T-1"));
    CoursesDTO saved = coursesService.save(course(participant, type));
    TrainerDTO trainer = trainerService.save(trainer("Tom"));
    trainerService.deleteById(trainer.getId());
    saved.setTrainerIds(Set.of(trainer.getId()));

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
    assertNotEquals(0, coursesService.findAll().size());
  }

  // ---- helpers ---------------------------------------------------------------------------------------------

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
