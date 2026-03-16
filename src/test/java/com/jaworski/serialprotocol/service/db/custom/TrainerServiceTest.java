package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({TrainerService.class})
class TrainerServiceTest {

  @Autowired
  private TrainerService trainerService;

  @Test
  void shouldSaveTrainer() {
    TrainerDTO trainer = createTrainer("Jan", "Kowalski", "jan.kowalski@test.pl");

    TrainerDTO saved = trainerService.save(trainer);

    assertNotNull(saved.getId());
    assertEquals("Jan", saved.getName());
    assertEquals("Kowalski", saved.getSurname());
    assertEquals("jan.kowalski@test.pl", saved.getEmail());
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
    return trainer;
  }
}

