package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional
public class InstructorService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private final InstructorRepository instructorRepository;

    @Transactional(readOnly = true)
    public List<Instructor> findAll() {
       return instructorRepository.findAll();
    }

    @Transactional(readOnly = true)
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

    /**
     * Stores the instructors not yet known by surname and name, in one transaction.
     *
     * <p>De-duplication is by name, not by the client-assigned number, because that is the
     * contract the sending system relies on. Atomic: a failing row fails the whole batch
     * instead of being logged and skipped, which used to leave the caller guessing.</p>
     *
     * @return the numbers of the instructors that were stored
     */
    public List<Integer> saveNew(Collection<Instructor> instructors) {
        List<Integer> saved = new ArrayList<>();
        for (Instructor instructor : instructors) {
            // Known by name among the live rows, or by number among all rows including tombstones:
            // the number is the primary key and a re-sent deleted instructor must not break the batch.
            if (findByNameAndSurname(instructor).isEmpty() && instructorRepository.countAnyByNo(instructor.getNo()) == 0) {
                saved.add(instructorRepository.save(instructor).getNo());
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Instructor> findByNameAndSurname(Instructor instructor) {
        return instructorRepository.findBySurnameAndName(instructor.getSurname(), instructor.getName());
    }
}
