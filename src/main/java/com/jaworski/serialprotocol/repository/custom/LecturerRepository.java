package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, UUID> {

  /** Image ids for a whole page in one query; see {@link ImageLink} for why not getImages(). */
  @Query("select p.uuid as owner, i.id as image from Lecturer p join p.images i where p.uuid in :owners")
  List<ImageLink> findImageLinks(@Param("owners") Collection<UUID> owners);
}
