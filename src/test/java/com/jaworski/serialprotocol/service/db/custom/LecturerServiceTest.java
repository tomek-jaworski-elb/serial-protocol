package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({LecturerService.class, ImageService.class, CoursesService.class, ParticipantService.class, CourseTypeService.class, TrainerService.class})
class LecturerServiceTest {

    @Autowired
    private LecturerService lecturerService;
    @Autowired
    private CoursesService coursesService;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private CourseTypeService courseTypeService;
    @Autowired
    private ImageService imageService;

    @Autowired
    private ImageRepository imageRepository;

    // --- findAll ---

    @Test
    void findAll_shouldReturnEmptyList_whenNoLecturers() {
        List<LecturerDTO> result = lecturerService.findAll();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_shouldReturnAllSavedLecturers() {
        lecturerService.save(createLecturer("Jan", "Kowalski"));
        lecturerService.save(createLecturer("Anna", "Nowak"));

        List<LecturerDTO> result = lecturerService.findAll();

        assertEquals(2, result.size());
    }

    // --- findById ---

    @Test
    void findById_shouldReturnDTO_whenLecturerExists() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));

        LecturerDTO result = lecturerService.findById(saved.getId());

        assertNotNull(result);
        assertEquals(saved.getId(), result.getId());
        assertEquals("Jan", result.getName());
        assertEquals("Kowalski", result.getSurname());
    }

    @Test
    void findById_shouldReturnNull_whenLecturerDoesNotExist() {
        LecturerDTO result = lecturerService.findById(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertNull(result);
    }

    // --- save ---

    @Test
    void save_shouldPersistLecturerAndReturnDTOWithId() {
        LecturerDTO dto = createLecturer("Maria", "Wiśniewska");

        LecturerDTO saved = lecturerService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Maria", saved.getName());
        assertEquals("Wiśniewska", saved.getSurname());
    }

    @Test
    void save_shouldPersistLecturerWithImages() {
        LecturerDTO dto = createLecturer("Piotr", "Zając");
        Set<UUID> images = createImages();
        dto.setImagesUuid(images);

        LecturerDTO saved = lecturerService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getImagesUuid());
        assertEquals(2, saved.getImagesUuid().size());
        assertTrue(saved.getImagesUuid().containsAll(images));
    }

    @Test
    void save_shouldPersistMultipleLecturers() {
        lecturerService.save(createLecturer("Jan", "Kowalski"));
        lecturerService.save(createLecturer("Anna", "Nowak"));
        lecturerService.save(createLecturer("Piotr", "Zając"));

        assertEquals(3, lecturerService.findAll().size());
    }

    // --- deleteById ---

    @Test
    void deleteById_shouldThrowWhenLecturerReferencedByCourse() {
        ParticipantDTO participant = new ParticipantDTO();
        participant.setName("Test");
        participant.setSurname("User");
        participant.setBirthDate(LocalDate.of(1990, 1, 1));
        participant = participantService.save(participant);

        CourseTypeDTO courseType = new CourseTypeDTO();
        courseType.setCode("L-1");
        courseType.setDescription("Test");
        courseType.setLongDescription("Test course");
        courseType = courseTypeService.save(courseType);

        LecturerDTO lecturer = lecturerService.save(createLecturer("Jan", "Kowalski"));

        CoursesDTO course = new CoursesDTO();
        course.setParticipantUuid(participant.getParticipantUuid());
        course.setCourseTypeId(courseType.getId());
        course.setStartDate(LocalDate.of(2025, 1, 1));
        course.setEndDate(LocalDate.of(2025, 1, 31));
        course.setLecturerIds(Set.of(lecturer.getId()));
        coursesService.save(course);

        UUID lecturerId = lecturer.getId();
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> lecturerService.deleteById(lecturerId)
        );
        assertTrue(exception.getMessage().contains("referenced by existing courses"));
    }

    @Test
    void deleteById_shouldRemoveLecturer() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));

        lecturerService.deleteById(saved.getId());
        Set<UUID> images = saved.getImagesUuid();
        assertNull(lecturerService.findById(saved.getId()));
        Image imageById = imageService.getImageById(images.stream().findFirst().orElseThrow());
        assertNull(imageById);
    }

    @Test
    void deleteById_shouldOnlyRemoveSpecifiedLecturer() {
        LecturerDTO first = lecturerService.save(createLecturer("Jan", "Kowalski"));
        LecturerDTO second = lecturerService.save(createLecturer("Anna", "Nowak"));

        lecturerService.deleteById(first.getId());

        assertNull(lecturerService.findById(first.getId()));
        assertNotNull(lecturerService.findById(second.getId()));
        assertEquals(1, lecturerService.findAll().size());
    }

    // --- updateById ---

    @Test
    void updateById_shouldUpdateLecturerFields() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));

        saved.setName("Janusz");
        saved.setSurname("Kowal");

        LecturerDTO updated = lecturerService.updateById(saved);

        assertNotNull(updated);
        assertEquals(saved.getId(), updated.getId());
        assertEquals("Janusz", updated.getName());
        assertEquals("Kowal", updated.getSurname());
    }

    @Test
  void updateById_shouldUpdateImages() {
    LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
    Set<UUID> newImages = createImages();
    saved.setImagesUuid(newImages);

    LecturerDTO updated = lecturerService.updateById(saved);

    assertEquals(newImages.size(), updated.getImagesUuid().size());
    assertTrue(updated.getImagesUuid().containsAll(newImages));
  }

    @Test
    void updateById_shouldUpdateEmail() {
      LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
      String newEmail = "newEmail";
      saved.setEmail(newEmail);
      LecturerDTO updated = lecturerService.updateById(saved);
      assertNotNull(updated);
      assertEquals(newEmail, updated.getEmail());
    }

    @Test
    void updateById_shouldUpdateNickname() {
      LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
      String newNickname = "newNickname";
      saved.setNickname(newNickname);
      LecturerDTO updated = lecturerService.updateById(saved);
      assertNotNull(updated);
      assertEquals(newNickname, updated.getNickname());
    }

    @Test
    void save_shouldPersistNotes() {
        LecturerDTO dto = createLecturer("Jan", "Kowalski");
        dto.setNotes("Some notes about the lecturer");
        LecturerDTO saved = lecturerService.save(dto);
        assertEquals("Some notes about the lecturer", saved.getNotes());
    }

    @Test
    void save_shouldPersistPhoneNumber() {
        LecturerDTO dto = createLecturer("Jan", "Kowalski");
        dto.setPhoneNumber("+48 123 456 789");
        LecturerDTO saved = lecturerService.save(dto);
        assertEquals("+48 123 456 789", saved.getPhoneNumber());
    }

    @Test
    void save_shouldPersistAddress() {
        LecturerDTO dto = createLecturer("Jan", "Kowalski");
        dto.setAddress("ul. Testowa 1, 00-001 Warszawa");
        LecturerDTO saved = lecturerService.save(dto);
        assertEquals("ul. Testowa 1, 00-001 Warszawa", saved.getAddress());
    }

    @Test
    void updateById_shouldUpdateNotes() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        saved.setNotes("Updated notes");
        LecturerDTO updated = lecturerService.updateById(saved);
        assertEquals("Updated notes", updated.getNotes());
    }

    @Test
    void updateById_shouldUpdatePhoneNumber() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        saved.setPhoneNumber("+48 999 888 777");
        LecturerDTO updated = lecturerService.updateById(saved);
        assertEquals("+48 999 888 777", updated.getPhoneNumber());
    }

    @Test
    void updateById_shouldUpdateAddress() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        saved.setAddress("ul. Nowa 5, 00-002 Kraków");
        LecturerDTO updated = lecturerService.updateById(saved);
        assertEquals("ul. Nowa 5, 00-002 Kraków", updated.getAddress());
    }

    @Test
    void updateById_shouldNotChangeRecordCount() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        saved.setName("Janusz");

        lecturerService.updateById(saved);

        assertEquals(1, lecturerService.findAll().size());
    }

    // --- pagination ---

    @Test
    void findAll_shouldReturnPageWithPagination() {
        lecturerService.save(createLecturer("Jan", "Kowalski"));
        lecturerService.save(createLecturer("Anna", "Nowak"));
        lecturerService.save(createLecturer("Piotr", "Zając"));

        Page<LecturerDTO> firstPage = lecturerService.findAll(PageRequest.of(0, 2));
        assertEquals(2, firstPage.getContent().size());
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());

        Page<LecturerDTO> secondPage = lecturerService.findAll(PageRequest.of(1, 2));
        assertEquals(1, secondPage.getContent().size());
    }

    @Test
    void findAll_shouldReturnEmptyPageWhenNoData() {
        Page<LecturerDTO> page = lecturerService.findAll(PageRequest.of(0, 10));
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    // --- helper ---

    private LecturerDTO createLecturer(String name, String surname) {
        LecturerDTO dto = new LecturerDTO();
        dto.setName(name);
        dto.setSurname(surname);
        dto.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@example.com");
        dto.setNickname(name.toLowerCase() + surname.toLowerCase());
        dto.setImagesUuid(createImages());
        return dto;
    }

    private Set<UUID> createImages() {
        Image image1 = new Image();
        image1.setData(new byte[]{4, 5, 6});
        image1.setContentType("content");
        Image image2 = new Image();
        image2.setData(new byte[]{1, 2, 0, 3, 4, 4, 5, 6});
        image2.setContentType("xyz");
        List<Image> saved = imageRepository.saveAll(List.of(image1, image2));
        Set<UUID> ids = new HashSet<>();
        saved.forEach(image -> ids.add(image.getId()));
        return ids;
    }
}
