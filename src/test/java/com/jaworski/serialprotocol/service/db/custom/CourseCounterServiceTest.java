package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({CourseCounterService.class, ImageService.class,
        CoursesService.class, ParticipantService.class, CourseTypeService.class,
        TrainerService.class, LecturerService.class, TechnicianService.class})
class CourseCounterServiceTest {

  @Autowired
  private CourseCounterService courseCounterService;
  @Autowired
  private CoursesService coursesService;
  @Autowired
  private ParticipantService participantService;
  @Autowired
  private CourseTypeService courseTypeService;

  @Autowired
  private ImageService imageService;

  @Test
  void testGetCourseCounter() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    assertEquals(toSave.counter(), save.counter());
    assertEquals(image.getId(), save.imageUuid());
    assertEquals(1, imageService.getAllImages());
  }

  @Test
  void testSaveImage() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    assertEquals(toSave.counter(), save.counter());
    assertEquals(image.getId(), save.imageUuid());
    assertEquals(1, imageService.getAllImages());
  }

  @Test
  void testGetImage() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    assertEquals(toSave.counter(), save.counter());
    assertEquals(image.getId(), save.imageUuid());
    assertEquals(1, imageService.getAllImages());
  }

  @Test
  void testDeleteCounter() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    Image savedImage = imageService.saveImage(new byte[]{1, 2, 3, 99, 9, 0}, "context text 2");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    assertEquals(toSave.counter(), save.counter());
    assertEquals(image.getId(), save.imageUuid());
    assertEquals(2, imageService.getAllImages());

    courseCounterService.delete(save.uuid());

    assertEquals(1, imageService.getAllImages());
    assertEquals(savedImage.getId(), imageService.getImageById(savedImage.getId()).getId());
  }

  @Test
  void testDeleteImage() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    assertEquals(toSave.counter(), save.counter());
    assertEquals(image.getId(), save.imageUuid());
    assertEquals(1, imageService.getAllImages());

//     Najpierw odłączamy obraz od CourseCounter
    CourseCounterDTO updated = new CourseCounterDTO(save.uuid(), save.counter(), null);
    courseCounterService.save(updated);

    // Teraz usuwamy obraz
    imageService.delete(image.getId());

    // Sprawdzamy, że CourseCounter nadal istnieje
    Optional<CourseCounterDTO> byUuid = courseCounterService.getByUuid(save.uuid());
    assertTrue(byUuid.isPresent());

    // Sprawdzamy, że liczba obrazów to 0
    assertEquals(0, imageService.getAllImages());

    // Sprawdzamy, że imageUuid jest null w CourseCounterDTO, a obraz już nie istnieje
    assertNull(byUuid.get().imageUuid());
    assertNull(imageService.getImageById(image.getId()));
  }

  @Test
  void testUpdateImage() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "context text");
    CourseCounterDTO toSave = new CourseCounterDTO(5L, image.getId());
    CourseCounterDTO save = courseCounterService.save(toSave);
    assertNotNull(save);
    imageService.delete(image.getId());
    var updatedImage = imageService.saveImage(new byte[]{1, 2, 33,4,5,6,7,7,8}, "context text new");
    CourseCounterDTO updated = new CourseCounterDTO(save.uuid() ,7L, updatedImage.getId());

    CourseCounterDTO update = courseCounterService.update(updated);
    assertEquals(updated.counter(), update.counter());
    assertEquals(updatedImage.getId(), update.imageUuid());
    assertEquals(1, imageService.getAllImages());

  }

  @Test
  void testFindAllWithPagination() {
    Image image1 = imageService.saveImage(new byte[]{1, 2, 3}, "context text 1");
    Image image2 = imageService.saveImage(new byte[]{4, 5, 6}, "context text 2");
    Image image3 = imageService.saveImage(new byte[]{7, 8, 9}, "context text 3");
    courseCounterService.save(new CourseCounterDTO(5L, image1.getId()));
    courseCounterService.save(new CourseCounterDTO(10L, image2.getId()));
    courseCounterService.save(new CourseCounterDTO(15L, image3.getId()));

    Page<CourseCounterDTO> firstPage = courseCounterService.findAll(PageRequest.of(0, 2));
    assertEquals(2, firstPage.getContent().size());
    assertEquals(3, firstPage.getTotalElements());
    assertEquals(2, firstPage.getTotalPages());

    Page<CourseCounterDTO> secondPage = courseCounterService.findAll(PageRequest.of(1, 2));
    assertEquals(1, secondPage.getContent().size());
  }

  @Test
  void testFindAllShouldReturnEmptyPageWhenNoData() {
    Page<CourseCounterDTO> page = courseCounterService.findAll(PageRequest.of(0, 10));
    assertTrue(page.getContent().isEmpty());
    assertEquals(0, page.getTotalElements());
  }

  @Test
  void delete_shouldThrowWhenCourseCounterReferencedByCourse() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "img");
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(99L, image.getId()));

    ParticipantDTO participant = new ParticipantDTO();
    participant.setName("Jan");
    participant.setSurname("Kowalski");
    participant.setBirthDate(LocalDate.of(1990, 1, 1));
    participant = participantService.save(participant);

    CourseTypeDTO courseType = new CourseTypeDTO();
    courseType.setCode("CC-TEST");
    courseType.setDescription("Test");
    courseType.setLongDescription("Test course");
    courseType = courseTypeService.save(courseType);

    CoursesDTO course = new CoursesDTO();
    course.setParticipantUuid(participant.getParticipantUuid());
    course.setCourseTypeId(courseType.getId());
    course.setStartDate(LocalDate.of(2025, 1, 1));
    course.setEndDate(LocalDate.of(2025, 1, 31));
    course.setCourseCounterUuid(counter.uuid());
    coursesService.save(course);

    UUID counterUuid = counter.uuid();
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> courseCounterService.delete(counterUuid)
    );
    assertTrue(exception.getMessage().contains("referenced by existing courses"));
  }

  @Test
  void delete_shouldSucceedWhenCourseCounterNotReferencedByCourse() {
    Image image = imageService.saveImage(new byte[]{1, 2, 3}, "img");
    CourseCounterDTO counter = courseCounterService.save(new CourseCounterDTO(77L, image.getId()));

    courseCounterService.delete(counter.uuid());

    assertTrue(courseCounterService.getByUuid(counter.uuid()).isEmpty());
  }

}