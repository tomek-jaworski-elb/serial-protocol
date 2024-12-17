package com.jaworski.serialprotocol.mappers;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;

public class StudentMapper {

    public static StudentDTO mapToDTO(Student student) {
        StudentDTO studentDTO = new StudentDTO();
        studentDTO.setId(student.getId());
        studentDTO.setName(student.getName());
        studentDTO.setLastName(student.getLastName());
        studentDTO.setCourseNo(student.getCourseNo());
        studentDTO.setDateBegine(student.getDateBegine());
        studentDTO.setDateEnd(student.getDateEnd());
        studentDTO.setMrMs(student.getMrMs());
        studentDTO.setCertType(student.getCertType());
        return studentDTO;
    }

    public static Student mapToEntity(StudentDTO studentDTO) {
        Student student = new Student();
        student.setId(studentDTO.getId());
        student.setName(studentDTO.getName());
        student.setLastName(studentDTO.getLastName());
        student.setCourseNo(studentDTO.getCourseNo());
        student.setDateBegine(studentDTO.getDateBegine());
        student.setDateEnd(studentDTO.getDateEnd());
        student.setMrMs(studentDTO.getMrMs());
        student.setCertType(studentDTO.getCertType());
        return student;
    }
}
