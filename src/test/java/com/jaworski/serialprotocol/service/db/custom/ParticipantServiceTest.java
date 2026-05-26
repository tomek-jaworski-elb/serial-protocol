package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({ParticipantService.class, CoursesService.class, CourseTypeService.class, TrainerService.class, LecturerService.class})
class ParticipantServiceTest {

    @Autowired
    private ParticipantService participantService;
    @Autowired
    private CoursesService coursesService;
    @Autowired
    private CourseTypeService courseTypeService;
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

        ParticipantDTO result = participantService.findByUuid(saved.getParticipantUuid());

        assertNotNull(result);
        assertEquals(saved.getParticipantUuid(), result.getParticipantUuid());
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

        assertNotNull(saved.getParticipantUuid());
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
    void deleteByUuid_shouldThrowWhenParticipantReferencedByCourse() {
        ParticipantDTO participant = participantService.save(createParticipant("Jan", "Kowalski"));

        CourseTypeDTO courseType = new CourseTypeDTO();
        courseType.setCode("P-1");
        courseType.setDescription("Test");
        courseType.setLongDescription("Test course");
        courseType = courseTypeService.save(courseType);

        CoursesDTO course = new CoursesDTO();
        course.setParticipantUuid(participant.getParticipantUuid());
        course.setCourseTypeId(courseType.getId());
        course.setStartDate(LocalDate.of(2025, 1, 1));
        course.setEndDate(LocalDate.of(2025, 1, 31));
        coursesService.save(course);

        UUID participantUuid = participant.getParticipantUuid();
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> participantService.deleteByUuid(participantUuid)
        );
        assertTrue(exception.getMessage().contains("referenced by existing courses"));
    }

    @Test
    void deleteByUuid_shouldRemoveParticipant() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        participantService.deleteByUuid(saved.getParticipantUuid());

        assertNull(participantService.findByUuid(saved.getParticipantUuid()));
    }

    @Test
    void deleteByUuid_shouldOnlyRemoveSpecifiedParticipant() {
        ParticipantDTO first = participantService.save(createParticipant("Jan", "Kowalski"));
        ParticipantDTO second = participantService.save(createParticipant("Anna", "Nowak"));

        participantService.deleteByUuid(first.getParticipantUuid());

        assertNull(participantService.findByUuid(first.getParticipantUuid()));
        assertNotNull(participantService.findByUuid(second.getParticipantUuid()));
        assertEquals(1, participantService.findAll().size());
    }

    // --- updateByUuid ---

    @Test
    void save_shouldPersistNotes() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setNotes("Medical notes");
        ParticipantDTO saved = participantService.save(dto);
        assertEquals("Medical notes", saved.getNotes());
    }

    @Test
    void save_shouldPersistNickname() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setNickname("jkowalski");
        ParticipantDTO saved = participantService.save(dto);
        assertEquals("jkowalski", saved.getNickname());
    }

    @Test
    void save_shouldPersistEmail() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setEmail("jan.kowalski@example.com");
        ParticipantDTO saved = participantService.save(dto);
        assertEquals("jan.kowalski@example.com", saved.getEmail());
    }

    @Test
    void save_shouldPersistPhoneNumber() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setPhoneNumber("+48 100 200 300");
        ParticipantDTO saved = participantService.save(dto);
        assertEquals("+48 100 200 300", saved.getPhoneNumber());
    }

    @Test
    void save_shouldPersistAddress() {
        ParticipantDTO dto = createParticipant("Jan", "Kowalski");
        dto.setAddress("ul. Główna 1, 00-001 Łódź");
        ParticipantDTO saved = participantService.save(dto);
        assertEquals("ul. Główna 1, 00-001 Łódź", saved.getAddress());
    }

    @Test
    void updateByUuid_shouldUpdateNotes() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setNotes("Updated notes");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        assertEquals("Updated notes", updated.getNotes());
    }

    @Test
    void updateByUuid_shouldUpdateNickname() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setNickname("newNick");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        assertEquals("newNick", updated.getNickname());
    }

    @Test
    void updateByUuid_shouldUpdateEmail() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setEmail("new@example.com");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        assertEquals("new@example.com", updated.getEmail());
    }

    @Test
    void updateByUuid_shouldUpdatePhoneNumber() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setPhoneNumber("+48 888 777 666");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        assertEquals("+48 888 777 666", updated.getPhoneNumber());
    }

    @Test
    void updateByUuid_shouldUpdateAddress() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));
        saved.setAddress("ul. Nowa 99, 00-099 Białystok");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        assertEquals("ul. Nowa 99, 00-099 Białystok", updated.getAddress());
    }

    @Test
    void updateByUuid_shouldUpdateNameAndSurname() {
        ParticipantDTO saved = participantService.save(createParticipant("Jan", "Kowalski"));

        saved.setName("Janusz");
        saved.setSurname("Kowal");

        ParticipantDTO updated = participantService.updateByUuid(saved);

        assertNotNull(updated);
        assertEquals(saved.getParticipantUuid(), updated.getParticipantUuid());
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

    // --- pagination ---

    @Test
    void findAll_shouldReturnPageWithPagination() {
        participantService.save(createParticipant("Jan", "Kowalski"));
        participantService.save(createParticipant("Anna", "Nowak"));
        participantService.save(createParticipant("Piotr", "Zając"));

        Page<ParticipantDTO> firstPage = participantService.findAll(PageRequest.of(0, 2));
        assertEquals(2, firstPage.getContent().size());
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());

        Page<ParticipantDTO> secondPage = participantService.findAll(PageRequest.of(1, 2));
        assertEquals(1, secondPage.getContent().size());
    }

    @Test
    void findAll_shouldReturnEmptyPageWhenNoData() {
        Page<ParticipantDTO> page = participantService.findAll(PageRequest.of(0, 10));
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
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
