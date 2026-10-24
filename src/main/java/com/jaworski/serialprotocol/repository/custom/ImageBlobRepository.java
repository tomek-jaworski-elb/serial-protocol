package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.ImageBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageBlobRepository extends JpaRepository<ImageBlob, UUID> {

  /** Only the thumbnail column: serving a thumbnail must not read the full-size blob. */
  @Query("select b.thumbData from ImageBlob b where b.id = :id")
  Optional<byte[]> findThumbData(@Param("id") UUID id);
}
