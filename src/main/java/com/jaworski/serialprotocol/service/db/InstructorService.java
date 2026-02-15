package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class InstructorService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private final InstructorRepository instructorRepository;

    public List<Instructor> findAll() {
       return instructorRepository.findAll();
    }

    public Page<Instructor> findAllPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("surname", "name"));
        return instructorRepository.findAll(pageable);
    }

    public Page<Instructor> findAllPaginated(int page) {
        return findAllPaginated(page, DEFAULT_PAGE_SIZE);
    }

    public Instructor save(Instructor instructor) {
        return instructorRepository.save(instructor);
    }

    public Optional<Instructor> findByNameAndSurname(Instructor instructor) {
        return instructorRepository.findBySurnameAndName(instructor.getSurname(), instructor.getName());
    }
}
