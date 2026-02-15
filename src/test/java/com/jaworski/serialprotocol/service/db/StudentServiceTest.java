package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({StudentService.class})
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    @Test
    void testGetAll() {
        assertTrue(studentService.getStudents().isEmpty());
        Student student = new Student();
        student.setId(1);
        student.setName("testName");
        student.setLastName("testLastName");
        student.setCourseNo("testCourseNo");
        student.setDateBegine(new java.util.Date());
        student.setDateEnd(new java.util.Date());
        student.setMrMs("testMrMs");
        student.setCertType("testCertType");
        studentService.save(student);
        assertEquals(1, studentService.getStudents().size());
        assertEquals(1, studentService.getLatestWeekAllStudents().size());
        assertEquals(student, studentService.getStudentById(1));
        assertInstanceOf(Student.class, studentService.getStudentById(1));
        assertInstanceOf(List.class, studentService.getStudents());
    }

    @Test
    void testGetStudentsPaginated_emptyDatabase() {
        Page<StudentDTO> page = studentService.getStudentsPaginated(0, 20);

        assertNotNull(page);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getTotalPages());
    }

    @Test
    void testGetStudentsPaginated_withDefaultPageSize() {
        // Create 25 students
        for (int i = 1; i <= 25; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(0);

        assertNotNull(page);
        assertEquals(20, page.getContent().size()); // Default page size is 20
        assertEquals(25, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(0, page.getNumber());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testGetStudentsPaginated_firstPage() {
        // Create 30 students
        for (int i = 1; i <= 30; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(0, 10);

        assertNotNull(page);
        assertEquals(10, page.getContent().size());
        assertEquals(30, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(0, page.getNumber());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testGetStudentsPaginated_middlePage() {
        // Create 30 students
        for (int i = 1; i <= 30; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(1, 10);

        assertNotNull(page);
        assertEquals(10, page.getContent().size());
        assertEquals(30, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getNumber());
        assertFalse(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void testGetStudentsPaginated_lastPage() {
        // Create 25 students
        for (int i = 1; i <= 25; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(2, 10);

        assertNotNull(page);
        assertEquals(5, page.getContent().size()); // Last page has only 5 elements
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(2, page.getNumber());
        assertFalse(page.isFirst());
        assertTrue(page.isLast());
    }

    @Test
    void testGetStudentsPaginated_customPageSize() {
        // Create 50 students
        for (int i = 1; i <= 50; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(0, 50);

        assertNotNull(page);
        assertEquals(50, page.getContent().size());
        assertEquals(50, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertTrue(page.isFirst());
        assertTrue(page.isLast());
    }

    @Test
    void testGetStudentsPaginated_pageOutOfRange() {
        // Create 10 students
        for (int i = 1; i <= 10; i++) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(5, 10); // Page 5 doesn't exist

        assertNotNull(page);
        assertTrue(page.getContent().isEmpty());
        assertEquals(10, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    void testGetStudentsPaginated_sortedById() {
        // Create students in reverse order
        for (int i = 10; i >= 1; i--) {
            studentService.save(createTestStudent(i));
        }

        Page<StudentDTO> page = studentService.getStudentsPaginated(0, 10);

        assertNotNull(page);
        assertEquals(10, page.getContent().size());
        // Verify sorted by ID ascending
        assertEquals(1, page.getContent().get(0).getId());
        assertEquals(10, page.getContent().get(9).getId());
    }

    private Student createTestStudent(int id) {
        Student student = new Student();
        student.setId(id);
        student.setName("Name" + id);
        student.setLastName("LastName" + id);
        student.setCourseNo("Course" + id);
        student.setDateBegine(new Date());
        student.setDateEnd(new Date());
        student.setMrMs("Mr");
        student.setCertType("Type" + id);
        return student;
    }
}