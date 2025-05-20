package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.Student;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {

    @Query(value = "SELECT * FROM student ORDER BY student_date_end DESC LIMIT 20", nativeQuery = true)
    Collection<Student> findStudentsOrderByDateBegine();

    @Query(value = "SELECT * FROM student WHERE student_visible = true ORDER BY student_date_end DESC LIMIT 20", nativeQuery = true)
    Collection<Student> findVisibleStudentsOrderByDateBegine();

    List<Student> findAllByVisible(boolean visible, Pageable pageable);

    @Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.staffs LEFT JOIN FETCH s.instructors")
    List<Student> findAllWithRelations();
}
