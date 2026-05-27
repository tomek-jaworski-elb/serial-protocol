package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseCounterRepository extends JpaRepository<CourseCounter, UUID> {

  @Query("SELECT COALESCE(MAX(c.counter), 0) FROM CourseCounter c")
  Long findMaxCounter();

  Optional<CourseCounter> findByCounter(Long counter);

  List<CourseCounter> findAllByUuidIn(Collection<UUID> uuids);
}
