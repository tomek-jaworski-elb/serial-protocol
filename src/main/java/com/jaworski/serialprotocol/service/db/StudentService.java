package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.mappers.StudentMapper;
import com.jaworski.serialprotocol.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public Student save(Student student) {
        return studentRepository.save(student);
    }

    public Collection<Student> getAll() {
        return studentRepository.findAll();
    }

    public Student setStudentShow(int id) {
        Student studentById = studentRepository.findById(id).orElse(null);
        if (studentById != null) {
            studentById.setVisible(true);
            return studentRepository.save(studentById);
        }
        return null;
    }

    public Student setStudentHide(int id) {
        Student studentById = studentRepository.findById(id).orElse(null);
        if (studentById != null) {
            studentById.setVisible(false);
            return studentRepository.save(studentById);
        }
        return null;
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

    public Collection<StudentDTO> getVisibleStudents() {
        List<Student> allByVisible =studentRepository.findAllByVisible(true, PageRequest.of(0, 20));
        return allByVisible.stream().map(StudentMapper::mapToDTO).toList();
    }

    public Collection<StudentDTO> getVisibleLatestWeekAllStudents() {
        return studentRepository.findVisibleStudentsOrderByDateBegine().stream()
                .map(StudentMapper::mapToDTO)
                .limit(20)
                .toList();
    }

    public Student findStudentById(int id) {
        return studentRepository.findById(id).orElse(null);
    }

}
