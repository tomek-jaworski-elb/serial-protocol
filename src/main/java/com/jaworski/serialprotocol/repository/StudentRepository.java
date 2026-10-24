package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.Student;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {

    Student findStudentById(int id);

    /** Native: a tombstoned student still owns its id, so the import must not insert it again. */
    @Query(value = "SELECT COUNT(*) FROM student WHERE student_id = :id", nativeQuery = true)
    long countAnyById(@Param("id") int id);

    /** JPQL rather than the old native SELECT, so soft-deleted students stay hidden here too. */
    @Query("select s from Student s order by s.dateEnd desc")
    List<Student> findLatest(Pageable pageable);

}
