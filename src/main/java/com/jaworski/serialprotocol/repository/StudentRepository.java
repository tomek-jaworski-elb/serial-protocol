package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {

    Student findStudentById(int id);

    @Query(value = "SELECT * FROM student ORDER BY student_date_end DESC LIMIT 20", nativeQuery = true)
    Collection<Student> findStudentsOrderByDateBegine();

}
