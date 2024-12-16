package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public Student save(Student student) {
        return studentRepository.save(student);
    }

    public List<Student> getAllStudents() {
       return studentRepository.findAll();
    }
}
