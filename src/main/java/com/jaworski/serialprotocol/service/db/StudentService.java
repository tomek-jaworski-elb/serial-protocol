package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public Student save(Student student) {
        return studentRepository.save(student);
    }

    @Nullable
    public Student getStudentById(int id) {
        return studentRepository.findStudentById((id));
    }
}
