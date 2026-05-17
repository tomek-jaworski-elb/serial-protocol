package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({CourseCounterService.class, ImageService.class})
class CourseCounterServiceTest {

  @Autowired
  private CourseCounterService courseCounterService;

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


}