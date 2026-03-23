package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({CoursesService.class, ParticipantService.class, CourseTypeService.class, TrainerService.class, LecturerService.class})
class CoursesServiceTest {

  @Autowired
  private CoursesService coursesService;
  @Autowired
  private ParticipantService participantService;
  @Autowired
  private CourseTypeService courseTypeService;
  @Autowired
  private TrainerService trainerService;
  @Autowired
  private LecturerService lecturerService;

  // --- findAll ---

  @Test
  void findAll_shouldReturnEmptyList_whenNoCourses() {
    List<CoursesDTO> result = coursesService.findAll();
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void findAll_shouldReturnAllSavedCourses() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-A");

    coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    coursesService.save(createCourse(participant.getUuid(), courseType.getId()));

    assertEquals(2, coursesService.findAll().size());
  }

  // --- findByUuid ---

  @Test
  void findByUuid_shouldReturnDTO_whenCourseExists() {
    ParticipantDTO participant = savedParticipant("Anna", "Nowak");
    CourseTypeDTO courseType = savedCourseType("NAV-B");

    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    CoursesDTO result = coursesService.findByUuid(saved.getUuid());

    assertNotNull(result);
    assertEquals(saved.getUuid(), result.getUuid());
    assertEquals(participant.getUuid(), result.getParticipantUuid());
    assertEquals(courseType.getId(), result.getCourseTypeId());
  }

  @Test
  void findByUuid_shouldReturnNull_whenCourseDoesNotExist() {
    assertNull(coursesService.findByUuid(UUID.randomUUID()));
  }

  // --- findByParticipantUuid ---

  @Test
  void findByParticipantUuid_shouldReturnCoursesForParticipant() {
    ParticipantDTO p1 = savedParticipant("Jan", "Kowalski");
    ParticipantDTO p2 = savedParticipant("Anna", "Nowak");
    CourseTypeDTO courseType = savedCourseType("NAV-C");

    coursesService.save(createCourse(p1.getUuid(), courseType.getId()));
    coursesService.save(createCourse(p1.getUuid(), courseType.getId()));
    coursesService.save(createCourse(p2.getUuid(), courseType.getId()));

    List<CoursesDTO> result = coursesService.findByParticipantUuid(p1.getUuid());
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(c -> p1.getUuid().equals(c.getParticipantUuid())));
  }

  @Test
  void findByParticipantUuid_shouldReturnEmptyList_whenParticipantHasNoCourses() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    List<CoursesDTO> result = coursesService.findByParticipantUuid(participant.getUuid());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  // --- nextId ---

  @Test
  void nextId_shouldReturnOne_whenNoCourses() {
    assertEquals(1L, coursesService.nextId());
  }

  @Test
  void nextId_shouldReturnMaxIdPlusOne() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-D");
    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setId(10L);
    coursesService.save(dto);

    assertEquals(11L, coursesService.nextId());
  }

  // --- save ---

  @Test
  void save_shouldPersistAndReturnDTOWithGeneratedId() {
    ParticipantDTO participant = savedParticipant("Maria", "Wiśniewska");
    CourseTypeDTO courseType = savedCourseType("NAV-E");

    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));

    assertNotNull(saved);
    assertNotNull(saved.getUuid());
    assertNotNull(saved.getId());
    assertEquals(participant.getUuid(), saved.getParticipantUuid());
    assertEquals(courseType.getId(), saved.getCourseTypeId());
  }

  @Test
  void save_shouldPersistDates() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-F");
    LocalDate start = LocalDate.of(2025, 1, 10);
    LocalDate end = LocalDate.of(2025, 1, 20);

    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setStartDate(start);
    dto.setEndDate(end);

    CoursesDTO saved = coursesService.save(dto);

    assertEquals(start, saved.getStartDate());
    assertEquals(end, saved.getEndDate());
  }

  @Test
  void save_shouldThrowException_whenParticipantUuidIsNull() {
    CourseTypeDTO courseType = savedCourseType("NAV-G");
    CoursesDTO dto = createCourse(null, courseType.getId());

    assertThrows(IllegalArgumentException.class, () -> coursesService.save(dto));
  }

  @Test
  void save_shouldThrowException_whenCourseTypeIdIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CoursesDTO dto = createCourse(participant.getUuid(), null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.save(dto));
  }

  @Test
  void save_shouldAutoAssignId_whenIdIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-H");
    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setId(null);

    CoursesDTO saved = coursesService.save(dto);

    assertNotNull(saved.getId());
    assertEquals(1L, saved.getId());
  }

  @Test
  void save_shouldPersistWithTrainers() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-I");
    TrainerDTO trainer = savedTrainer("Adam", "Nowak");

    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setTrainerIds(Set.of(trainer.getId()));

    CoursesDTO saved = coursesService.save(dto);

    assertEquals(1, saved.getTrainerIds().size());
    assertTrue(saved.getTrainerIds().contains(trainer.getId()));
  }

  @Test
  void save_shouldPersistWithLecturers() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-J");
    LecturerDTO lecturer = savedLecturer("Ewa", "Zielińska");

    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setLecturerIds(Set.of(lecturer.getLecturerId()));

    CoursesDTO saved = coursesService.save(dto);

    assertEquals(1, saved.getLecturerIds().size());
    assertTrue(saved.getLecturerIds().contains(lecturer.getLecturerId()));
  }

  // --- deleteByUuid ---

  @Test
  void deleteByUuid_shouldRemoveCourse() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-K");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));

    coursesService.deleteByUuid(saved.getUuid());

    assertNull(coursesService.findByUuid(saved.getUuid()));
    assertEquals(0, coursesService.findAll().size());
  }

  @Test
  void deleteByUuid_shouldOnlyRemoveSpecifiedCourse() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-L");
    CoursesDTO first = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    CoursesDTO second = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));

    coursesService.deleteByUuid(first.getUuid());

    assertNull(coursesService.findByUuid(first.getUuid()));
    assertNotNull(coursesService.findByUuid(second.getUuid()));
    assertEquals(1, coursesService.findAll().size());
  }

  // --- update ---

  @Test
  void update_shouldUpdateDates() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-M");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));

    LocalDate newStart = LocalDate.of(2026, 5, 1);
    LocalDate newEnd = LocalDate.of(2026, 5, 15);
    saved.setStartDate(newStart);
    saved.setEndDate(newEnd);

    CoursesDTO updated = coursesService.update(saved);

    assertEquals(newStart, updated.getStartDate());
    assertEquals(newEnd, updated.getEndDate());
    assertEquals(saved.getUuid(), updated.getUuid());
  }

  @Test
  void update_shouldChangeCourseType() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO type1 = savedCourseType("NAV-N1");
    CourseTypeDTO type2 = savedCourseType("NAV-N2");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), type1.getId()));

    saved.setCourseTypeId(type2.getId());
    CoursesDTO updated = coursesService.update(saved);

    assertEquals(type2.getId(), updated.getCourseTypeId());
  }

  @Test
  void update_shouldNotChangeRecordCount() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-O");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    saved.setStartDate(LocalDate.of(2025, 2, 1));
    saved.setEndDate(LocalDate.of(2025, 2, 28));

    coursesService.update(saved);

    assertEquals(1, coursesService.findAll().size());
  }

  @Test
  void update_shouldThrowException_whenUuidIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-P");
    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(dto));
  }

  @Test
  void update_shouldThrowException_whenCourseDoesNotExist() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-Q");
    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setUuid(UUID.randomUUID());

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(dto));
  }

  @Test
  void update_shouldThrowException_whenParticipantUuidIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-R");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    saved.setParticipantUuid(null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
  }

  @Test
  void update_shouldThrowException_whenCourseTypeIdIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-S");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    saved.setCourseTypeId(null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
  }

  @Test
  void save_shouldThrowException_whenEndDateIsBeforeStartDate() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-DATE");
    CoursesDTO dto = createCourse(participant.getUuid(), courseType.getId());
    dto.setStartDate(LocalDate.of(2025, 5, 1));
    dto.setEndDate(LocalDate.of(2025, 4, 1));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> coursesService.save(dto));
    assertTrue(ex.getMessage().contains("end date") || ex.getMessage().contains("End date"));
  }

  @Test
  void update_shouldThrowException_whenEndDateIsBeforeStartDate() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-DATE2");
    CoursesDTO saved = coursesService.save(createCourse(participant.getUuid(), courseType.getId()));
    saved.setStartDate(LocalDate.of(2025, 5, 1));
    saved.setEndDate(LocalDate.of(2025, 4, 1));

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
  }

  // --- helpers ---

  private CoursesDTO createCourse(UUID participantUuid, Long courseTypeId) {
    CoursesDTO dto = new CoursesDTO();
    dto.setParticipantUuid(participantUuid);
    dto.setCourseTypeId(courseTypeId);
    dto.setStartDate(LocalDate.of(2025, 1, 1));
    dto.setEndDate(LocalDate.of(2025, 1, 31));
    return dto;
  }

  private ParticipantDTO savedParticipant(String name, String surname) {
    ParticipantDTO dto = new ParticipantDTO();
    dto.setName(name);
    dto.setSurname(surname);
    dto.setBirthDate(LocalDate.of(1990, 1, 1));
    return participantService.save(dto);
  }

  private CourseTypeDTO savedCourseType(String code) {
    CourseTypeDTO dto = new CourseTypeDTO();
    dto.setCode(code);
    dto.setDescription("Description for " + code);
    dto.setLongDescription("Long description for " + code);
    return courseTypeService.save(dto);
  }

  private TrainerDTO savedTrainer(String name, String surname) {
    TrainerDTO dto = new TrainerDTO();
    dto.setName(name);
    dto.setSurname(surname);
    dto.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@example.com");
    return trainerService.save(dto);
  }

  private LecturerDTO savedLecturer(String name, String surname) {
    LecturerDTO dto = new LecturerDTO();
    dto.setName(name);
    dto.setSurname(surname);
    return lecturerService.save(dto);
  }
}
