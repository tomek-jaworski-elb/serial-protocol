package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class InstructorService {

    private final InstructorRepository instructorRepository;

    public List<Instructor> findAll() {
       return instructorRepository.findAll();
    }

    public Instructor save(Instructor instructor) {
        return instructorRepository.save(instructor);
    }

    public Optional<Instructor> findByNameAndSurname(Instructor instructor) {
        return instructorRepository.findBySurnameAndName(instructor.getSurname(), instructor.getName());
    }
}
