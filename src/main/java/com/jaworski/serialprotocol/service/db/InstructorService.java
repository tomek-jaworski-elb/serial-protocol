package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstructorService {

    private final InstructorRepository instructorRepository;

    public void save(Instructor instructor) {
        instructorRepository.save(instructor);
    }

    public Instructor findInstructorById(int id) {
        return instructorRepository.findById(id).orElse(null);
    }
}
