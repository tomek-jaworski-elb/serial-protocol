package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.IdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {

  /** SELECT … FOR UPDATE: the caller's transaction owns the row until it commits. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<IdSequence> findByName(String name);
}
