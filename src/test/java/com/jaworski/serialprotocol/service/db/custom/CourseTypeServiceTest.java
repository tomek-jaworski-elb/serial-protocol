package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({CourseTypeService.class, CoursesService.class, ParticipantService.class, TrainerService.class, LecturerService.class})
class CourseTypeServiceTest {

  @Autowired
  private CourseTypeService courseTypeService;
  @Autowired
  private CoursesService coursesService;
  @Autowired
  private ParticipantService participantService;

  @Test
  void shouldSaveCourseType() {
    CourseTypeDTO courseType = createCourseType("C-A", "Basic", "Basic navigation course");

    CourseTypeDTO saved = courseTypeService.save(courseType);

    assertNotNull(saved.getId());
    assertEquals("C-A", saved.getCode());
    assertEquals("Basic", saved.getDescription());
    assertEquals("Basic navigation course", saved.getLongDescription());
  }

  @Test
  void shouldFindAllCourseTypes() {
    assertTrue(courseTypeService.findAll().isEmpty());

    courseTypeService.save(createCourseType("C-A", "Basic", "Basic navigation course"));
    courseTypeService.save(createCourseType("C-B", "Advanced", "Advanced navigation course"));

    List<CourseTypeDTO> result = courseTypeService.findAll();

    assertEquals(2, result.size());
  }

  @Test
  void shouldFindByIdWhenCourseTypeExists() {
    CourseTypeDTO saved = courseTypeService.save(createCourseType("C-A", "Basic", "Basic navigation course"));

    CourseTypeDTO found = courseTypeService.findById(saved.getId());

    assertNotNull(found);
    assertEquals(saved.getId(), found.getId());
    assertEquals("C-A", found.getCode());
  }

  @Test
  void shouldThrowWhenCourseTypeNotFoundById() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> courseTypeService.findById(9999L)
    );

    assertEquals("Course type with id 9999 not found", exception.getMessage());
  }

  @Test
  void shouldDeleteCourseTypeById() {
    CourseTypeDTO saved = courseTypeService.save(createCourseType("C-A", "Basic", "Basic navigation course"));

    courseTypeService.deleteById(saved.getId());

    assertTrue(courseTypeService.findAll().isEmpty());
  }

  @Test
  void shouldUpdateCourseType() {
    CourseTypeDTO saved = courseTypeService.save(createCourseType("C-A", "Basic", "Basic navigation course"));

    saved.setCode("C-B");
    saved.setDescription("Advanced");
    saved.setLongDescription("Advanced navigation course");

    CourseTypeDTO updated = courseTypeService.update(saved);

    assertEquals(saved.getId(), updated.getId());
    assertEquals("C-B", updated.getCode());
    assertEquals("Advanced", updated.getDescription());
    assertEquals("Advanced navigation course", updated.getLongDescription());
  }

  @Test
  void shouldThrowWhenUpdatingCourseTypeWithoutId() {
    CourseTypeDTO withoutId = createCourseType("C-A", "Basic", "Basic navigation course");

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> courseTypeService.update(withoutId)
    );

    assertEquals("Course type id is required for update", exception.getMessage());
  }

  @Test
  void shouldThrowWhenUpdatingNonExistingCourseType() {
    CourseTypeDTO nonExisting = createCourseType("C-A", "Basic", "Basic navigation course");
    nonExisting.setId(9999L);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> courseTypeService.update(nonExisting)
    );

    assertEquals("Course type with id 9999 not found", exception.getMessage());
  }

  @Test
  void shouldThrowWhenDeletingCourseTypeReferencedByCourse() {
    CourseTypeDTO courseType = courseTypeService.save(createCourseType("C-REF", "Referenced", "Referenced course type"));

    ParticipantDTO participant = new ParticipantDTO();
    participant.setId(1L);
    participant.setName("Test");
    participant.setSurname("User");
    participant.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
    participant = participantService.save(participant);

    CoursesDTO course = new CoursesDTO();
    course.setParticipantUuid(participant.getUuid());
    course.setCourseTypeId(courseType.getId());
    course.setStartDate(LocalDate.of(2025, 1, 1));
    course.setEndDate(LocalDate.of(2025, 1, 31));
    coursesService.save(course);

    Long id = courseType.getId();
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> courseTypeService.deleteById(id)
    );
    assertTrue(exception.getMessage().contains("referenced by existing courses"));
  }

  private CourseTypeDTO createCourseType(String code, String description, String longDescription) {
    CourseTypeDTO courseType = new CourseTypeDTO();
    courseType.setCode(code);
    courseType.setDescription(description);
    courseType.setLongDescription(longDescription);
    return courseType;
  }
}
