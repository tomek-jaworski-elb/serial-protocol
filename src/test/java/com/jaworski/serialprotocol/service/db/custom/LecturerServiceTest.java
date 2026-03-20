package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({LecturerService.class})
class LecturerServiceTest {

    @Autowired
    private LecturerService lecturerService;

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

        LecturerDTO result = lecturerService.findById(saved.getLecturerId());

        assertNotNull(result);
        assertEquals(saved.getLecturerId(), result.getLecturerId());
        assertEquals("Jan", result.getName());
        assertEquals("Kowalski", result.getSurname());
    }

    @Test
    void findById_shouldReturnNull_whenLecturerDoesNotExist() {
        LecturerDTO result = lecturerService.findById(9999L);
        assertNull(result);
    }

    // --- save ---

    @Test
    void save_shouldPersistLecturerAndReturnDTOWithId() {
        LecturerDTO dto = createLecturer("Maria", "Wiśniewska");

        LecturerDTO saved = lecturerService.save(dto);

        assertNotNull(saved);
        assertNotNull(saved.getLecturerId());
        assertEquals("Maria", saved.getName());
        assertEquals("Wiśniewska", saved.getSurname());
    }

    @Test
    void save_shouldPersistLecturerWithPhoto() {
        LecturerDTO dto = createLecturer("Piotr", "Zając");
        byte[] photo = {1, 2, 3, 4};
        dto.setPhoto(photo);

        LecturerDTO saved = lecturerService.save(dto);

        assertNotNull(saved);
        assertArrayEquals(photo, saved.getPhoto());
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
    void deleteById_shouldRemoveLecturer() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));

        lecturerService.deleteById(saved.getLecturerId());

        assertNull(lecturerService.findById(saved.getLecturerId()));
    }

    @Test
    void deleteById_shouldOnlyRemoveSpecifiedLecturer() {
        LecturerDTO first = lecturerService.save(createLecturer("Jan", "Kowalski"));
        LecturerDTO second = lecturerService.save(createLecturer("Anna", "Nowak"));

        lecturerService.deleteById(first.getLecturerId());

        assertNull(lecturerService.findById(first.getLecturerId()));
        assertNotNull(lecturerService.findById(second.getLecturerId()));
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
        assertEquals(saved.getLecturerId(), updated.getLecturerId());
        assertEquals("Janusz", updated.getName());
        assertEquals("Kowal", updated.getSurname());
    }

    @Test
    void updateById_shouldUpdatePhoto() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        byte[] newPhoto = {5, 6, 7, 8};
        saved.setPhoto(newPhoto);

        LecturerDTO updated = lecturerService.updateById(saved);

        assertArrayEquals(newPhoto, updated.getPhoto());
    }

    @Test
    void updateById_shouldNotChangeRecordCount() {
        LecturerDTO saved = lecturerService.save(createLecturer("Jan", "Kowalski"));
        saved.setName("Janusz");

        lecturerService.updateById(saved);

        assertEquals(1, lecturerService.findAll().size());
    }

    // --- helper ---

    private LecturerDTO createLecturer(String name, String surname) {
        LecturerDTO dto = new LecturerDTO();
        dto.setName(name);
        dto.setSurname(surname);
        return dto;
    }
}
