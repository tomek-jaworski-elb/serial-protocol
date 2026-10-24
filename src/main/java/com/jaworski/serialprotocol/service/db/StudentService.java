package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.mappers.StudentMapper;
import com.jaworski.serialprotocol.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentService implements Repository<Student> {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private final StudentRepository studentRepository;

    public Student save(Student student) {
        return studentRepository.save(student);
    }

    /**
     * Stores the students whose id is not yet known, in one transaction.
     *
     * <p>The REST endpoint used to look each one up and save it in separate transactions, so a
     * batch could be half-applied. Now either every new student of the batch is stored or none.</p>
     *
     * <p>"Known" means any row with that id, including a tombstoned one: the sending system owns
     * the ids and may re-send a student this installation has deleted.</p>
     *
     * @return the ids that were stored
     */
    public List<Integer> saveNew(Collection<Student> students) {
        List<Integer> saved = new ArrayList<>();
        for (Student student : students) {
            // Native count: a soft-deleted student is hidden from getStudentById but still owns
            // its id, and re-inserting it would fail the whole batch on the primary key.
            if (studentRepository.countAnyById(student.getId()) == 0) {
                saved.add(studentRepository.save(student).getId());
            }
        }
        return saved;
    }

    @Nullable
    @Transactional(readOnly = true)
    public Student getStudentById(int id) {
        return studentRepository.findStudentById((id));
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Student> getAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Collection<StudentDTO> getLatestWeekAllStudents() {
        Collection<Student> latestWeekAllStudents = studentRepository.findLatest(PageRequest.of(0, DEFAULT_PAGE_SIZE));
        if (latestWeekAllStudents.isEmpty()) {
            return Collections.emptyList();
        } else {
            return latestWeekAllStudents.stream()
                    .map(StudentMapper::mapToDTO).toList();
        }
    }

    @Transactional(readOnly = true)
    public Page<StudentDTO> getStudentsPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return studentRepository.findAll(pageable)
                .map(StudentMapper::mapToDTO);
    }

    public Page<StudentDTO> getStudentsPaginated(int page) {
        return getStudentsPaginated(page, DEFAULT_PAGE_SIZE);
    }
}
