package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseCounterRepository extends JpaRepository<CourseCounter, UUID> {

  /** Native: must see soft-deleted rows, whose numbers stay taken in the unique index. */
  @Query(value = "SELECT COALESCE(MAX(course_counter_counter), 0) FROM course_counter", nativeQuery = true)
  Long findMaxCounter();

  /** Native for the same reason: a deleted counter still owns its number. */
  @Query(value = "SELECT COUNT(*) FROM course_counter WHERE course_counter_counter = :counter AND course_counter_uuid <> :uuid",
      nativeQuery = true)
  long countWithCounterExcludingUuid(@Param("counter") Long counter, @Param("uuid") UUID uuid);

  @Query(value = "SELECT COUNT(*) FROM course_counter WHERE course_counter_counter = :counter", nativeQuery = true)
  long countWithCounter(@Param("counter") Long counter);

  Optional<CourseCounter> findByCounter(Long counter);

  List<CourseCounter> findAllByUuidIn(Collection<UUID> uuids);
}
