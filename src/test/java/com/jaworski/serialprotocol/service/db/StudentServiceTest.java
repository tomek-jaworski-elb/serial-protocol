package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.entity.Staff;
import com.jaworski.serialprotocol.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({StudentService.class, StaffService.class, InstructorService.class})
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private InstructorService instructorService;

    @Test
    void saveInstructor() {
        Instructor instructor = new Instructor();
        instructor.setId(1);
        instructor.setName("testName");
        instructor.setLastName("testSurname");
        instructorService.save(instructor);
        Instructor instructorById = instructorService.findInstructorById(1);
        assertEquals(instructor, instructorById);
    }

    @Test
    void saveStaff() {
        Staff staff = new Staff();
        staff.setId(1);
        staff.setName("testName");
        staff.setSurname("testSurname");
        staffService.save(staff);
        Staff staffById = staffService.findStaffById(1);
        assertEquals(staff, staffById);
    }

    @Test
    void testGetAll() {
        assertTrue(studentService.getStudents().isEmpty());
        Student student = new Student();
        student.setId(2);
        student.setName("testName");
        student.setLastName("testLastName");
        student.setCourseNo("testCourseNo");
        student.setDateBegine(new java.util.Date());
        student.setDateEnd(new java.util.Date());
        student.setMrMs("testMrMs");
        student.setCertType("testCertType");
        student.setVisible(true);
        Staff staff = new Staff();
        staff.setId(2);
        staff.setName("testName");
        staff.setSurname("testSurname");
        staffService.save(staff);
        student.setStaffs(Set.of(staff));
        Instructor instructor = new Instructor();
        instructor.setId(1);
        instructor.setName("testName");
        instructor.setLastName("testLastName");
        instructorService.save(instructor);
        student.setInstructors(Set.of(instructor));
        studentService.save(student);
        assertEquals(1, studentService.getStudents().size());
        assertEquals(1, studentService.getLatestWeekAllStudents().size());
        assertEquals(student, studentService.findStudentById(2));
        assertInstanceOf(Student.class, studentService.findStudentById(2));
        assertInstanceOf(List.class, studentService.getStudents());
        Student studentById = studentService.findStudentById(2);
        boolean visible = studentById.isVisible();
        assertTrue(visible);
        Set<Staff> staffs = studentById.getStaffs();
        assertEquals(1, staffs.size());
        assertEquals("testName", staffs.iterator().next().getName());
        Set<Instructor> instructors = studentById.getInstructors();
        assertEquals(1, instructors.size());
        assertEquals("testName", instructors.iterator().next().getName());
    }

}