package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {

  /**
   * Reads only the thumbnail columns.
   *
   * <p>Loading the whole entity would drag the full-size blob along with it: the
   * {@code @Basic(fetch = LAZY)} on {@code data} has no effect here because the build
   * does not run hibernate-enhance-maven-plugin, so field-level laziness is inactive.
   * Without this projection a thumbnail request would still read the original from
   * the database and only the network transfer would shrink.</p>
   */
  @Query("select i.thumbData as thumbData, i.thumbContentType as thumbContentType "
      + "from Image i where i.id = :id")
  Optional<ThumbnailView> findThumbnailById(@Param("id") UUID id);

  interface ThumbnailView {
    byte[] getThumbData();

    String getThumbContentType();
  }
}
