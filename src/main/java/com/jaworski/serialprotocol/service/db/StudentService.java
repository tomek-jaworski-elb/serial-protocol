package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.mappers.StudentMapper;
import com.jaworski.serialprotocol.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

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

    public Collection<StudentDTO> getStudents() {
        var students = studentRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(StudentMapper::mapToDTO)
                .toList();
        if (students.isEmpty()) {
            return Collections.emptyList();
        } else {
            return students;
        }
    }

    public Collection<StudentDTO> getLatestWeekAllStudents() {
        Collection<Student> latestWeekAllStudents = studentRepository.findStudentsOrderByDateBegine();
        if (latestWeekAllStudents.isEmpty()) {
            return Collections.emptyList();
        } else {
            return latestWeekAllStudents.stream()
                    .map(StudentMapper::mapToDTO).toList();
        }
    }
}
