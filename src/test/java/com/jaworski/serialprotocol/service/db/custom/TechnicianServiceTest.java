package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

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
@Import({TechnicianService.class, ImageService.class, CoursesService.class, ParticipantService.class,
        CourseTypeService.class, TrainerService.class, LecturerService.class})
class TechnicianServiceTest {

    @Autowired
    private TechnicianService technicianService;
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
    void findAll_shouldReturnEmptyList_whenNoTechnicians() {
        List<TechnicianDTO> result = technicianService.findAll();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_shouldReturnAllSavedTechnicians() {
        technicianService.save(createTechnician("Jan", "Kowalski"));
        technicianService.save(createTechnician("Anna", "Nowak"));

        List<TechnicianDTO> result = technicianService.findAll();

        assertEquals(2, result.size());
    }

    // --- findById ---

    @Test
    void findById_shouldReturnDTO_whenTechnicianExists() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));

        TechnicianDTO result = technicianService.findById(saved.getId());

        assertNotNull(result);
        assertEquals(saved.getId(), result.getId());
        assertEquals("Jan", result.getName());
        assertEquals("Kowalski", result.getSurname());
    }

    @Test
    void findById_shouldReturnNull_whenTechnicianDoesNotExist() {
        TechnicianDTO result = technicianService.findById(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertNull(result);
    }

    // --- save ---

    @Test
    void save_shouldPersistTechnicianAndReturnDTOWithId() {
        TechnicianDTO dto = createTechnician("Maria", "Wiśniewska");

        TechnicianDTO saved = technicianService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Maria", saved.getName());
        assertEquals("Wiśniewska", saved.getSurname());
    }

    @Test
    void save_shouldPersistTechnicianWithImages() {
        TechnicianDTO dto = createTechnician("Piotr", "Zając");
        Set<UUID> images = createImages();
        dto.setImagesUuid(images);

        TechnicianDTO saved = technicianService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getImagesUuid());
        assertEquals(2, saved.getImagesUuid().size());
        assertTrue(saved.getImagesUuid().containsAll(images));
    }

    @Test
    void save_shouldPersistMultipleTechnicians() {
        technicianService.save(createTechnician("Jan", "Kowalski"));
        technicianService.save(createTechnician("Anna", "Nowak"));
        technicianService.save(createTechnician("Piotr", "Zając"));

        assertEquals(3, technicianService.findAll().size());
    }

    // --- deleteById ---

    @Test
    void deleteById_shouldThrowWhenTechnicianReferencedByCourse() {
        ParticipantDTO participant = new ParticipantDTO();
        participant.setName("Test");
        participant.setSurname("User");
        participant.setBirthDate(LocalDate.of(1990, 1, 1));
        participant = participantService.save(participant);

        CourseTypeDTO courseType = new CourseTypeDTO();
        courseType.setCode("T-1");
        courseType.setDescription("Test");
        courseType.setLongDescription("Test course");
        courseType = courseTypeService.save(courseType);

        TechnicianDTO technician = technicianService.save(createTechnician("Jan", "Kowalski"));

        CoursesDTO course = new CoursesDTO();
        course.setParticipantUuid(participant.getParticipantUuid());
        course.setCourseTypeId(courseType.getId());
        course.setStartDate(LocalDate.of(2025, 1, 1));
        course.setEndDate(LocalDate.of(2025, 1, 31));
        course.setTechnicianIds(Set.of(technician.getId()));
        coursesService.save(course);

        UUID technicianId = technician.getId();
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> technicianService.deleteById(technicianId)
        );
        assertTrue(exception.getMessage().contains("referenced by existing courses"));
    }

    @Test
    void deleteById_shouldRemoveTechnician() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));

        technicianService.deleteById(saved.getId());
        Set<UUID> images = saved.getImagesUuid();
        assertNull(technicianService.findById(saved.getId()));
        if (images != null && !images.isEmpty()) {
            Image imageById = imageService.getImageById(images.stream().findFirst().orElseThrow());
            assertNull(imageById);
        }
    }

    @Test
    void deleteById_shouldOnlyRemoveSpecifiedTechnician() {
        TechnicianDTO first = technicianService.save(createTechnician("Jan", "Kowalski"));
        TechnicianDTO second = technicianService.save(createTechnician("Anna", "Nowak"));

        technicianService.deleteById(first.getId());

        assertNull(technicianService.findById(first.getId()));
        assertNotNull(technicianService.findById(second.getId()));
        assertEquals(1, technicianService.findAll().size());
    }

    // --- updateById ---

    @Test
    void updateById_shouldUpdateTechnicianFields() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));

        saved.setName("Janusz");
        saved.setSurname("Kowal");

        TechnicianDTO updated = technicianService.updateById(saved);

        assertNotNull(updated);
        assertEquals(saved.getId(), updated.getId());
        assertEquals("Janusz", updated.getName());
        assertEquals("Kowal", updated.getSurname());
    }

    @Test
    void updateById_shouldUpdateImages() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));
        Set<UUID> newImages = createImages();
        saved.setImagesUuid(newImages);

        TechnicianDTO updated = technicianService.updateById(saved);

        assertEquals(newImages.size(), updated.getImagesUuid().size());
        assertTrue(updated.getImagesUuid().containsAll(newImages));
    }

    @Test
    void updateById_shouldUpdateEmail() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));
        String newEmail = "newemail@example.com";
        saved.setEmail(newEmail);
        TechnicianDTO updated = technicianService.updateById(saved);
        assertNotNull(updated);
        assertEquals(newEmail, updated.getEmail());
    }

    @Test
    void updateById_shouldUpdateNickname() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));
        String newNickname = "newNickname";
        saved.setNickname(newNickname);
        TechnicianDTO updated = technicianService.updateById(saved);
        assertNotNull(updated);
        assertEquals(newNickname, updated.getNickname());
    }

    @Test
    void updateById_shouldNotChangeRecordCount() {
        TechnicianDTO saved = technicianService.save(createTechnician("Jan", "Kowalski"));
        saved.setName("Janusz");

        technicianService.updateById(saved);

        assertEquals(1, technicianService.findAll().size());
    }

    @Test
    void updateById_shouldThrowWhenIdIsNull() {
        TechnicianDTO dto = createTechnician("Jan", "Kowalski");
        dto.setId(null);
        assertThrows(IllegalArgumentException.class, () -> technicianService.updateById(dto));
    }

    @Test
    void updateById_shouldThrowWhenTechnicianNotFound() {
        TechnicianDTO dto = createTechnician("Jan", "Kowalski");
        dto.setId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> technicianService.updateById(dto));
    }

    // --- helper ---

    private TechnicianDTO createTechnician(String name, String surname) {
        TechnicianDTO dto = new TechnicianDTO();
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

