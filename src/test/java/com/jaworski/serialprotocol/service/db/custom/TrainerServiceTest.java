package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({TrainerService.class})
class TrainerServiceTest {

  @Autowired
  private TrainerService trainerService;
  @Autowired
  private ImageRepository imageRepository;

  @Test
  void shouldSaveTrainer() {
    TrainerDTO trainer = createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl");

    TrainerDTO saved = trainerService.save(trainer);

    assertNotNull(saved.getId());
    assertEquals("Jan", saved.getName());
    assertEquals("Kowalski", saved.getSurname());
    assertEquals("jan.kowalski@test.pl", saved.getEmail());
    assertEquals(2, saved.getImagesUuid().size());
  }

  @Test
  void shouldFindAllTrainers() {
    assertTrue(trainerService.findAll().isEmpty());

    trainerService.save(createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl"));
    trainerService.save(createTrainer("Anna", "Nowak", "anna.nowak@test.pl"));

    List<TrainerDTO> result = trainerService.findAll();

    assertEquals(2, result.size());
  }

  @Test
  void shouldUpdateTrainer() {
    TrainerDTO saved = trainerService.save(createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl"));

    saved.setName("Janusz");
    saved.setSurname("Kowal");
    saved.setEmail("janusz.kowal@test.pl");

    TrainerDTO updated = trainerService.update(saved);

    assertEquals(saved.getId(), updated.getId());
    assertEquals("Janusz", updated.getName());
    assertEquals("Kowal", updated.getSurname());
    assertEquals("janusz.kowal@test.pl", updated.getEmail());
  }

  @Test
  void shouldUpdateImage() {
    TrainerDTO saved = trainerService.save(createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl"));
    Set<UUID> previousImages = saved.getImagesUuid();
    saved.setName("Janusz");
    saved.setSurname("Kowal");
    saved.setEmail("janusz.kowal@test.pl");
    Set<UUID> updatedImageIds = createImages(1);
    saved.setImagesUuid(updatedImageIds);

    TrainerDTO updated = trainerService.update(saved);
    assertEquals(saved.getId(), updated.getId());
    assertEquals("Janusz", updated.getName());
    assertEquals("Kowal", updated.getSurname());
    assertEquals("janusz.kowal@test.pl", updated.getEmail());
    assertNotEquals(previousImages, updated.getImagesUuid());
    assertEquals(1, updated.getImagesUuid().size());
    assertEquals(updatedImageIds, updated.getImagesUuid());
  }

  @Test
  void shouldThrowWhenUpdatingTrainerWithoutId() {
    TrainerDTO trainerWithoutId = createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl");

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> trainerService.update(trainerWithoutId)
    );

    assertEquals("Trainer id is required for update", exception.getMessage());
  }

  @Test
  void shouldThrowWhenUpdatingNonExistingTrainer() {
    TrainerDTO nonExisting = createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl");
    nonExisting.setId(9999L);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> trainerService.update(nonExisting)
    );

    assertEquals("Trainer with id 9999 not found", exception.getMessage());
  }

  private TrainerDTO createTrainer(String name, String surname, String email) {
    TrainerDTO trainer = new TrainerDTO();
    trainer.setName(name);
    trainer.setSurname(surname);
    trainer.setEmail(email);
    trainer.setImagesUuid(getImages());
    return trainer;
  }

  private Set<UUID> getImages() {
    return createImages(2);
  }

  private Set<UUID> createImages(int count) {
    Set<Image> images = new java.util.HashSet<>();
    for (int i = 0; i < count; i++) {
      Image image = new Image();
      image.setData(new byte[]{11, 22, 1, 2, 3, 4, (byte) (50 + i)});
      image.setContentType("context-" + i);
      images.add(image);
    }
    return imageRepository.saveAll(images).stream()
        .map(Image::getId)
        .collect(java.util.stream.Collectors.toSet());
  }
}

