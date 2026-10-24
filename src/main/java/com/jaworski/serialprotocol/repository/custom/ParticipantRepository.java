package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

  // Native on purpose: these must see soft-deleted rows. A number that belongs to a deleted
  // participant is still taken in the unique index, and the allocator must never go below it.
  @Query(value = "SELECT COALESCE(MAX(participants_id), 0) FROM participants", nativeQuery = true)
  Long findMaxParticipantId();

  @Query(value = "SELECT COUNT(*) FROM participants WHERE participants_id = :id", nativeQuery = true)
  long countWithParticipantId(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM participants WHERE participants_id = :id AND participants_uuid <> :uuid",
      nativeQuery = true)
  long countWithParticipantIdExcludingUuid(@Param("id") Long id, @Param("uuid") UUID uuid);
}
