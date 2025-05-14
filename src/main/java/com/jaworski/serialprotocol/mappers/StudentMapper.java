package com.jaworski.serialprotocol.mappers;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;

import java.util.Base64;

public class StudentMapper {

    private StudentMapper() {}

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
        byte[] photo = student.getPhoto() == null ? new byte[0] : student.getPhoto();
        String encoded = Base64.getEncoder().encodeToString(photo);
        studentDTO.setPhoto(encoded);
        studentDTO.setVisible(student.isVisible());
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
        String photo = studentDTO.getPhoto() == null ? "" : studentDTO.getPhoto();
        Base64.Decoder decoder = Base64.getDecoder();
        student.setPhoto(decoder.decode(photo));
        student.setVisible(studentDTO.isVisible());
        return student;
    }
}
