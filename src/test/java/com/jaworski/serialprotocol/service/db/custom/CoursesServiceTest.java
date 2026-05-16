package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({CoursesService.class, ParticipantService.class, CourseTypeService.class, TrainerService.class, LecturerService.class, TechnicianService.class, ImageService.class})
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
  @Autowired
  private TechnicianService technicianService;
  @Autowired
  private ImageRepository imageRepository;

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

    coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));

    assertEquals(2, coursesService.findAll().size());
  }

  // --- findByUuid ---

  @Test
  void findByUuid_shouldReturnDTO_whenCourseExists() {
    ParticipantDTO participant = savedParticipant("Anna", "Nowak");
    CourseTypeDTO courseType = savedCourseType("NAV-B");

    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    CoursesDTO result = coursesService.findByUuid(saved.getUuid());

    assertNotNull(result);
    assertEquals(saved.getUuid(), result.getUuid());
    assertEquals(participant.getParticipantUuid(), result.getParticipantUuid());
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

    coursesService.save(createCourse(p1.getParticipantUuid(), courseType.getId()));
    coursesService.save(createCourse(p1.getParticipantUuid(), courseType.getId()));
    coursesService.save(createCourse(p2.getParticipantUuid(), courseType.getId()));

    List<CoursesDTO> result = coursesService.findByParticipantUuid(p1.getParticipantUuid());
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(c -> p1.getParticipantUuid().equals(c.getParticipantUuid())));
  }

  @Test
  void findByParticipantUuid_shouldReturnEmptyList_whenParticipantHasNoCourses() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    List<CoursesDTO> result = coursesService.findByParticipantUuid(participant.getParticipantUuid());
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
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
    dto.setId(10L);
    coursesService.save(dto);

    assertEquals(11L, coursesService.nextId());
  }

  // --- save ---

  @Test
  void save_shouldPersistAndReturnDTOWithGeneratedId() {
    ParticipantDTO participant = savedParticipant("Maria", "Wiśniewska");
    CourseTypeDTO courseType = savedCourseType("NAV-E");

    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));

    assertNotNull(saved);
    assertNotNull(saved.getUuid());
    assertNotNull(saved.getId());
    assertEquals(participant.getParticipantUuid(), saved.getParticipantUuid());
    assertEquals(courseType.getId(), saved.getCourseTypeId());
  }

  @Test
  void save_shouldPersistDates() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-F");
    LocalDate start = LocalDate.of(2025, 1, 10);
    LocalDate end = LocalDate.of(2025, 1, 20);

    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
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
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.save(dto));
  }

  @Test
  void save_shouldAutoAssignId_whenIdIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-H");
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
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

    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
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

    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
    dto.setLecturerIds(Set.of(lecturer.getId()));

    CoursesDTO saved = coursesService.save(dto);

    assertEquals(1, saved.getLecturerIds().size());
    assertTrue(saved.getLecturerIds().contains(lecturer.getId()));
  }

  @Test
  void save_shouldPersistWithTechnicians() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-TECH");
    TechnicianDTO technician = savedTechnician("Marek", "Technik");

    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
    dto.setTechnicianIds(Set.of(technician.getId()));

    CoursesDTO saved = coursesService.save(dto);

    assertEquals(1, saved.getTechnicianIds().size());
    assertTrue(saved.getTechnicianIds().contains(technician.getId()));
  }

  // --- deleteByUuid ---

  @Test
  void deleteByUuid_shouldRemoveCourse() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-K");
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));

    coursesService.deleteByUuid(saved.getUuid());

    assertNull(coursesService.findByUuid(saved.getUuid()));
    assertEquals(0, coursesService.findAll().size());
  }

  @Test
  void deleteByUuid_shouldOnlyRemoveSpecifiedCourse() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-L");
    CoursesDTO first = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    CoursesDTO second = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));

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
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));

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
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), type1.getId()));

    saved.setCourseTypeId(type2.getId());
    CoursesDTO updated = coursesService.update(saved);

    assertEquals(type2.getId(), updated.getCourseTypeId());
  }

  @Test
  void update_shouldNotChangeRecordCount() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-O");
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    saved.setStartDate(LocalDate.of(2025, 2, 1));
    saved.setEndDate(LocalDate.of(2025, 2, 28));

    coursesService.update(saved);

    assertEquals(1, coursesService.findAll().size());
  }

  @Test
  void update_shouldThrowException_whenUuidIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-P");
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(dto));
  }

  @Test
  void update_shouldThrowException_whenCourseDoesNotExist() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-Q");
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
    dto.setUuid(UUID.randomUUID());

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(dto));
  }

  @Test
  void update_shouldThrowException_whenParticipantUuidIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-R");
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    saved.setParticipantUuid(null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
  }

  @Test
  void update_shouldThrowException_whenCourseTypeIdIsNull() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-S");
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
    saved.setCourseTypeId(null);

    assertThrows(IllegalArgumentException.class, () -> coursesService.update(saved));
  }

  @Test
  void save_shouldThrowException_whenEndDateIsBeforeStartDate() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-DATE");
    CoursesDTO dto = createCourse(participant.getParticipantUuid(), courseType.getId());
    dto.setStartDate(LocalDate.of(2025, 5, 1));
    dto.setEndDate(LocalDate.of(2025, 4, 1));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> coursesService.save(dto));
    assertTrue(ex.getMessage().contains("end date") || ex.getMessage().contains("End date"));
  }

  @Test
  void update_shouldThrowException_whenEndDateIsBeforeStartDate() {
    ParticipantDTO participant = savedParticipant("Jan", "Kowalski");
    CourseTypeDTO courseType = savedCourseType("NAV-DATE2");
    CoursesDTO saved = coursesService.save(createCourse(participant.getParticipantUuid(), courseType.getId()));
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

  private UUID getImage() {
    Image image = new Image();
    image.setData(new byte[]{7, 7, 7, 7});
    image.setContentType("participant-context");
    return imageRepository.save(image).getId();
  }

  private ParticipantDTO savedParticipant(String name, String surname) {
    ParticipantDTO dto = new ParticipantDTO();
    dto.setName(name);
    dto.setSurname(surname);
    dto.setBirthDate(LocalDate.of(1990, 1, 1));
    dto.setImage(getImage());
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
    dto.setImagesUuid(createImages());
    return trainerService.save(dto);
  }

  private LecturerDTO savedLecturer(String name, String surname) {
    LecturerDTO dto = new LecturerDTO();
    dto.setName(name);
    dto.setSurname(surname);
    dto.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@example.com");
    dto.setNickname(name.toLowerCase() + surname.toLowerCase());
    dto.setImagesUuid(createImages());
    return lecturerService.save(dto);
  }

  private TechnicianDTO savedTechnician(String name, String surname) {
    TechnicianDTO dto = new TechnicianDTO();
    dto.setName(name);
    dto.setSurname(surname);
    dto.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@example.com");
    dto.setNickname(name.toLowerCase() + surname.toLowerCase());
    dto.setImagesUuid(createImages());
    return technicianService.save(dto);
  }

  private Set<UUID> createImages() {
    var img1 = new Image();
    img1.setData(new byte[]{1, 2, 3, 4, 55, 54, 3, 1});
    img1.setContentType("content");
    var img2 = new Image();
    img2.setData(new byte[]{1, 2, 3, 4, 55, 54, 3, 10, 0, 0, 0, 0, 0});
    img2.setContentType("text content");
    return imageRepository.saveAll(List.of(img1, img2)).stream()
        .map(Image::getId)
        .collect(Collectors.toSet());
  }
}
