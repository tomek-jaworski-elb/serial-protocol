package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

  @Query("SELECT COALESCE(MAX(p.id), 0) FROM Participant p")
  Long findMaxParticipantId();

  @Query("SELECT COUNT(p) FROM Participant p WHERE p.id = :id")
  long countWithParticipantId(@Param("id") Long id);

  @Query("SELECT COUNT(p) FROM Participant p WHERE p.id = :id AND p.uuid <> :uuid")
  long countWithParticipantIdExcludingUuid(@Param("id") Long id, @Param("uuid") UUID uuid);
}
