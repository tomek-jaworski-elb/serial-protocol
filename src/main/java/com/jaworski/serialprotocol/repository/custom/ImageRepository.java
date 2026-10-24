package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * Records a thumbnail state without an entity save: a bulk statement leaves {@code version},
   * {@code updated_at} and {@code origin_node} alone, so a rebuild is not an edit of the photo
   * for the USB-stick synchronisation.
   */
  @Modifying(clearAutomatically = true)
  @Query("update Image i set i.thumbContentType = :contentType, i.thumbVersion = :version where i.id = :id")
  int updateThumbnailState(@Param("id") UUID id, @Param("contentType") String contentType,
      @Param("version") String version);

}
