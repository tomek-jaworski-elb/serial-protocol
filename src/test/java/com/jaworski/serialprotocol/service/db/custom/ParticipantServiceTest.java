package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({ParticipantService.class})
class ParticipantServiceTest {

    @Autowired
    private ParticipantService participantService;
    @Autowired
    private ImageRepository imageRepository;

    // --- findAll ---

    @Test
    void findAll_shouldReturnEmptyList_whenNoParticipants() {
        List<ParticipantDTO> result = participantService.findAll();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_shouldReturnAllSavedParticipants() {
        participantService.save(createParticipant("Jan", "Kowalski"));
        participantService.save(createParticipant("Anna", "Nowak"));

        List<ParticipantDTO> result = participantService.findAll();

        assertEquals(2, result.size());
    }

    // --- findByUuid ---

    @Test
    void findByUuid_shouldReturnDTO_whenParticipantExists() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        ParticipantDTO result = participantService.findByUuid(saved.getUuid());

        assertNotNull(result);
        assertEquals(saved.getUuid(), result.getUuid());
        assertEquals("Jan", result.getName());
        assertEquals("Kowalski", result.getSurname());
    }

    @Test
    void findByUuid_shouldReturnNull_whenParticipantDoesNotExist() {
        ParticipantDTO result = participantService.findByUuid(UUID.randomUUID());
        assertNull(result);
    }

    // --- nextId ---

    @Test
    void nextId_shouldReturnOne_whenNoParticipants() {
        assertEquals(1L, participantService.nextId());
    }

    @Test
    void nextId_shouldReturnMaxIdPlusOne() {
        participantService.save(createParticipantWithId("Jan", "Kowalski", 5L));
        assertEquals(6L, participantService.nextId());
    }

    // --- save ---

    @Test
    void save_shouldPersistAndReturnDTOWithGeneratedId() {
        ParticipantDTO dto = createParticipant("Maria", "Wiśniewska");

        ParticipantDTO saved = participantService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Maria", saved.getName());
        assertEquals("Wiśniewska", saved.getSurname());
    }

    @Test
    void save_shouldGenerateUuid() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        assertNotNull(saved.getUuid());
    }

    @Test
    void save_shouldPersistBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 6, 15);
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setBirthDate(birthDate);

        ParticipantDTO saved = participantService.save(dto);

        assertEquals(birthDate, saved.getBirthDate());
    }

    @Test
    void save_shouldPersistWithPhoto() {
        ParticipantDTO dto = createParticipant("Piotr", "Zając");
        UUID imageId = createAndSaveImageId();
        dto.setImage(imageId);

        ParticipantDTO saved = participantService.save(dto);

        assertEquals(imageId, saved.getImage());
    }

    @Test
    void save_shouldPersistMultipleParticipants() {
        participantService.save(createParticipant("Jan", "Kowalski"));
        participantService.save(createParticipant("Anna", "Nowak"));
        participantService.save(createParticipant("Piotr", "Zając"));

        assertEquals(3, participantService.findAll().size());
    }

    @Test
    void save_shouldThrowException_whenIdAlreadyExists() {
        participantService.save(createParticipantWithId("Jan", "Kowalski", 100L));

        ParticipantDTO duplicate = createParticipantWithId("Anna", "Nowak", 100L);
        assertThrows(IllegalArgumentException.class, () -> participantService.save(duplicate));
    }

    @Test
    void save_shouldAutoIncrementId_whenIdIsNull() {
        ParticipantDTO first = participantService.save(createParticipantWithId("Jan", "Kowalski", 10L));
        ParticipantDTO second = participantService.save(createParticipant("Anna", "Nowak"));

        assertEquals(11L, second.getId());
    }

    // --- deleteByUuid ---

    @Test
    void deleteByUuid_shouldRemoveParticipant() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        participantService.deleteByUuid(saved.getUuid());

        assertNull(participantService.findByUuid(saved.getUuid()));
    }

    @Test
    void deleteByUuid_shouldOnlyRemoveSpecifiedParticipant() {
        ParticipantDTO first = participantService.save(createParticipant("Jan", "Kowalski"));
        ParticipantDTO second = participantService.save(createParticipant("Anna", "Nowak"));

        participantService.deleteByUuid(first.getUuid());

        assertNull(participantService.findByUuid(first.getUuid()));
        assertNotNull(participantService.findByUuid(second.getUuid()));
        assertEquals(1, participantService.findAll().size());
    }

    // --- updateByUuid ---

    @Test
    void updateByUuid_shouldUpdateNameAndSurname() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        saved.setName("Janusz");
        saved.setSurname("Kowal");

        ParticipantDTO updated = participantService.updateByUuid(saved);

        assertNotNull(updated);
        assertEquals(saved.getUuid(), updated.getUuid());
        assertEquals("Janusz", updated.getName());
        assertEquals("Kowal", updated.getSurname());
    }

    @Test
    void updateByUuid_shouldUpdateBirthDate() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        LocalDate newDate = LocalDate.of(1985, 3, 20);
        saved.setBirthDate(newDate);

        ParticipantDTO updated = participantService.updateByUuid(saved);

        assertEquals(newDate, updated.getBirthDate());
    }

    @Test
    void updateByUuid_shouldUpdatePhoto() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        UUID newImageId = createAndSaveImageId();
        saved.setImage(newImageId);

        ParticipantDTO updated = participantService.updateByUuid(saved);

        assertEquals(newImageId, updated.getImage());
    }

  @Test
  void updateByUuid_shouldUpdateImage() {
    ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
    UUID newImage = createAndSaveImageId();
    saved.setImage(newImage);
    ParticipantDTO updated = participantService.updateByUuid(saved);

    assertEquals(newImage, updated.getImage());
  }

    @Test
    void updateByUuid_shouldNotChangeRecordCount() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setName("Janusz");

        participantService.updateByUuid(saved);

        assertEquals(1, participantService.findAll().size());
    }

    @Test
    void updateByUuid_shouldAllowKeepingSameId() {
        ParticipantDTO saved = participantService.save(createParticipantWithId("Jan", "Kowalski", 10L));
        saved.setName("Janusz");

        assertDoesNotThrow(() -> participantService.updateByUuid(saved));
    }

    @Test
    void updateByUuid_shouldThrowException_whenIdUsedByAnotherParticipant() {
        ParticipantDTO p1 = participantService.save(createParticipantWithId("Jan", "Kowalski", 1L));
        ParticipantDTO p2 = participantService.save(createParticipantWithId("Anna", "Nowak", 2L));

        p2.setId(p1.getId());
        assertThrows(IllegalArgumentException.class, () -> participantService.updateByUuid(p2));
    }

    @Test
    void updateByUuid_shouldThrowException_whenUuidIsNull() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        assertThrows(IllegalArgumentException.class, () -> participantService.updateByUuid(dto));
    }

    // --- helpers ---

    private ParticipantDTO createParticipant(String name, String surname) {
        ParticipantDTO dto = new ParticipantDTO();
        dto.setName(name);
        dto.setSurname(surname);
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setImage(getImage());
        return dto;
    }

    private ParticipantDTO createParticipantWithId(String name, String surname, Long id) {
        ParticipantDTO dto = createParticipant(name, surname);
        dto.setId(id);
        return dto;
    }

    private UUID getImage() {
      return createAndSaveImageId();
    }

    private UUID createAndSaveImageId() {
      Image image = new Image();
      image.setData(new byte[]{1, 2, 3, 4});
      image.setContentType("context");
      return imageRepository.save(image).getId();
    }
}
