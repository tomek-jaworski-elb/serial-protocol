package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

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

}