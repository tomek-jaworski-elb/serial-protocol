package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.SyncNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncNodeRepository extends JpaRepository<SyncNode, UUID> {

  /** The single row; the table never holds more than one. */
  Optional<SyncNode> findFirstByOrderByIdAsc();
}
