package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Integer> {

    Optional<Instructor> findBySurnameAndName(String surname, String name);


    /** Native: a tombstoned instructor still owns its number, so the import must not insert it again. */
    @Query(value = "SELECT COUNT(*) FROM instructor WHERE instructor_no = :no", nativeQuery = true)
    long countAnyByNo(@Param("no") int no);
}
