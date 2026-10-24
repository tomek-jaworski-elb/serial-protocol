package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.IdSequence;
import com.jaworski.serialprotocol.repository.custom.IdSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.LongSupplier;

/**
 * Hands out human-visible business numbers inside the caller's transaction.
 *
 * <p>Every sequence is one locked row of {@code id_sequence}, so two forms submitted at the
 * same time get two different numbers, and a number is only consumed when the record that
 * uses it commits. The highest value already in use is consulted on every allocation:
 * a user may still type a number of their own choosing, and the sequence must step over
 * it rather than hand it out a second time. That also lets a database that predates the
 * table (the H2 test profile, an installation whose migration has not run) start the row
 * from its current contents.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IdAllocator {

  public static final String PARTICIPANT = "participant";
  public static final String COURSES = "courses";
  public static final String COURSE_COUNTER = "course_counter";

  private final IdSequenceRepository repository;

  /**
   * Reserves the next free value of {@code sequence}.
   *
   * @param highestInUse the highest value a record currently holds, or 0 when there is none
   */
  public long next(String sequence, LongSupplier highestInUse) {
    IdSequence row = repository.findByName(sequence)
        .orElseGet(() -> new IdSequence(sequence, 1));
    long value = Math.max(row.getNext(), highestInUse.getAsLong() + 1);
    row.setNext(value + 1);
    repository.save(row);
    return value;
  }

  /**
   * The value {@link #next} would hand out now, without reserving it.
   *
   * <p>For the "next free" hint on the add forms only. Nothing may submit it as a value:
   * two open forms would show the same hint, and the number is assigned on save.</p>
   */
  @Transactional(readOnly = true)
  public long peek(String sequence, LongSupplier highestInUse) {
    long next = repository.findById(sequence).map(IdSequence::getNext).orElse(1L);
    return Math.max(next, highestInUse.getAsLong() + 1);
  }
}
