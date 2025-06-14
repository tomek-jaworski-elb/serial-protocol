package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Integer> {

    Optional<Instructor> findBySurnameAndName(String surname, String name);

}
