package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {

  /** Rows whose thumbnail state was produced by another generator version, or never. */
  @Query("select i.id from Image i where i.thumbVersion is null or i.thumbVersion <> :version")
  List<UUID> findIdsWithStaleThumbnail(@Param("version") String version);

}
