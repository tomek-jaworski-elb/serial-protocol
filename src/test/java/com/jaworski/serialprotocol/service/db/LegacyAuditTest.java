package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.entity.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy Student/Instructor rows carry the same audit columns and tombstones as the custom
 * domain, and the REST import stores a batch atomically while skipping what it already knows.
 */
@DataJpaTest
@Import({StudentService.class, InstructorService.class})
class LegacyAuditTest {

    @Autowired private StudentService studentService;
    @Autowired private InstructorService instructorService;
    @Autowired private EntityManager entityManager;

    @Test
    void aStudentStartsAtVersionZeroWithTimestamps_andADeleteIsATombstone() {
        Student student = new Student();
        student.setId(4711);
        student.setName("Jan");
        student.setLastName("Kowalski");
        student.setDateBegine(new Date());
        student.setDateEnd(new Date());
        Student saved = studentService.save(student);
        entityManager.flush();

        assertEquals(0L, saved.getVersion());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        entityManager.remove(saved);
        entityManager.flush();
        entityManager.clear();

        assertNull(studentService.getStudentById(4711), "hidden from every ordinary query");
        Number rows = (Number) entityManager
            .createNativeQuery("select count(*) from student where student_id = 4711").getSingleResult();
        assertEquals(1L, rows.longValue(), "but the row is still there");
        assertTrue(studentService.getLatestWeekAllStudents().stream().noneMatch(s -> s.getId() == 4711),
            "the latest-students list is JPQL now and does not show tombstones");
    }

    @Test
    void theImportSkipsStudentsItAlreadyKnows() {
        Student known = new Student();
        known.setId(1);
        known.setName("Known");
        known.setLastName("Student");
        Student fresh = new Student();
        fresh.setId(2);
        fresh.setName("Fresh");
        fresh.setLastName("Student");
        studentService.save(known);

        List<Integer> stored = studentService.saveNew(List.of(known, fresh));

        assertEquals(List.of(2), stored);
    }

    @Test
    void aReSentStudentThatWasDeletedHereIsSkipped_notReinserted() {
        Student student = new Student();
        student.setId(77);
        student.setName("Gone");
        student.setLastName("Student");
        studentService.save(student);
        entityManager.flush();
        entityManager.remove(entityManager.find(Student.class, 77));
        entityManager.flush();
        entityManager.clear();

        Student again = new Student();
        again.setId(77);
        again.setName("Gone");
        again.setLastName("Student");
        Student fresh = new Student();
        fresh.setId(78);
        fresh.setName("New");
        fresh.setLastName("Student");

        assertEquals(List.of(78), studentService.saveNew(List.of(again, fresh)),
            "the tombstone still owns id 77, so only 78 is stored and the batch succeeds");
    }

    @Test
    void theImportDeduplicatesInstructorsByName_andAuditsThem() {
        Instructor first = instructor(10, "Anna", "Nowak");
        Instructor sameName = instructor(11, "Anna", "Nowak");
        Instructor other = instructor(12, "Ewa", "Lis");

        List<Integer> stored = instructorService.saveNew(List.of(first, sameName, other));
        entityManager.flush();

        assertEquals(List.of(10, 12), stored);
        assertNotNull(instructorService.findByNameAndSurname(first).orElseThrow().getCreatedAt());
    }

    private static Instructor instructor(int no, String name, String surname) {
        Instructor instructor = new Instructor();
        instructor.setNo(no);
        instructor.setName(name);
        instructor.setSurname(surname);
        return instructor;
    }
}
