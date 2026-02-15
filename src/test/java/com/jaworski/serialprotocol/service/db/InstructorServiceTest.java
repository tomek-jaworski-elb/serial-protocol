package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({InstructorService.class})
class InstructorServiceTest {

    @Autowired
    private InstructorService instructorService;

    @Test
    void testFindAll_emptyDatabase() {
        List<Instructor> instructors = instructorService.findAll();

        assertNotNull(instructors);
        assertTrue(instructors.isEmpty());
    }

    @Test
    void testFindAllPaginated_emptyDatabase() {
        Page<Instructor> page = instructorService.findAllPaginated(0, 20);

        assertNotNull(page);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getTotalPages());
    }

    @Test
    void testFindAllPaginated_withDefaultPageSize() {
        // Create 25 instructors
        for (int i = 1; i <= 25; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(0);

        assertNotNull(page);
        assertEquals(20, page.getContent().size()); // Default page size is 20
        assertEquals(25, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(0, page.getNumber());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testFindAllPaginated_firstPage() {
        // Create 30 instructors
        for (int i = 1; i <= 30; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(0, 10);

        assertNotNull(page);
        assertEquals(10, page.getContent().size());
        assertEquals(30, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(0, page.getNumber());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testFindAllPaginated_middlePage() {
        // Create 30 instructors
        for (int i = 1; i <= 30; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(1, 10);

        assertNotNull(page);
        assertEquals(10, page.getContent().size());
        assertEquals(30, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getNumber());
        assertFalse(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testFindAllPaginated_lastPage() {
        // Create 25 instructors
        for (int i = 1; i <= 25; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(2, 10);

        assertNotNull(page);
        assertEquals(5, page.getContent().size()); // Last page has only 5 elements
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(2, page.getNumber());
        assertFalse(page.isFirst());
        assertTrue(page.isLast());
    }

    @Test
    void testFindAllPaginated_customPageSize() {
        // Create 50 instructors
        for (int i = 1; i <= 50; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(0, 50);

        assertNotNull(page);
        assertEquals(50, page.getContent().size());
        assertEquals(50, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertTrue(page.isFirst());
        assertTrue(page.isLast());
    }

    @Test
    void testFindAllPaginated_pageOutOfRange() {
        // Create 10 instructors
        for (int i = 1; i <= 10; i++) {
            instructorService.save(createTestInstructor(i));
        }

        Page<Instructor> page = instructorService.findAllPaginated(5, 10); // Page 5 doesn't exist

        assertNotNull(page);
        assertTrue(page.getContent().isEmpty());
        assertEquals(10, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    void testFindAllPaginated_sortedBySurnameAndName() {
        // Create instructors with different surnames
        instructorService.save(createTestInstructor(1, "Zebra", "Adam"));
        instructorService.save(createTestInstructor(2, "Alpha", "Zoe"));
        instructorService.save(createTestInstructor(3, "Alpha", "Anna"));
        instructorService.save(createTestInstructor(4, "Beta", "John"));

        Page<Instructor> page = instructorService.findAllPaginated(0, 10);

        assertNotNull(page);
        assertEquals(4, page.getContent().size());
        // Verify sorted by surname, then by name
        assertEquals("Alpha", page.getContent().get(0).getSurname());
        assertEquals("Anna", page.getContent().get(0).getName());
        assertEquals("Alpha", page.getContent().get(1).getSurname());
        assertEquals("Zoe", page.getContent().get(1).getName());
        assertEquals("Beta", page.getContent().get(2).getSurname());
        assertEquals("Zebra", page.getContent().get(3).getSurname());
    }

    @Test
    void testSaveAndFind() {
        Instructor instructor = createTestInstructor(1);

        Instructor saved = instructorService.save(instructor);

        assertNotNull(saved);
        assertEquals(1, saved.getNo());
        assertEquals(1, instructorService.findAll().size());
    }

    @Test
    void testFindByNameAndSurname() {
        Instructor instructor = createTestInstructor(1, "Smith", "John");
        instructorService.save(instructor);

        var found = instructorService.findByNameAndSurname(instructor);

        assertTrue(found.isPresent());
        assertEquals("John", found.get().getName());
        assertEquals("Smith", found.get().getSurname());
    }

    @Test
    void testFindByNameAndSurname_notFound() {
        Instructor instructor = createTestInstructor(1, "Smith", "John");

        var found = instructorService.findByNameAndSurname(instructor);

        assertTrue(found.isEmpty());
    }

    private Instructor createTestInstructor(int id) {
        return createTestInstructor(id, "Surname" + id, "Name" + id);
    }

    private Instructor createTestInstructor(int id, String surname, String name) {
        Instructor instructor = new Instructor();
        instructor.setNo(id);
        instructor.setName(name);
        instructor.setSurname(surname);
        instructor.setEmail(name.toLowerCase() + "@test.com");
        instructor.setPhone("123456789");
        instructor.setMobile("987654321");
        return instructor;
    }
}

