package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CourseTypeRepository extends JpaRepository<CourseType, UUID> {

  // Case-insensitive on purpose: the MariaDB default collation already treats "abc" and "ABC"
  // as the same unique value, so the pre-check has to agree with the constraint (H2 is
  // case-sensitive). Native so that a code owned by a soft-deleted type still counts as taken.
  @Query(value = "SELECT COUNT(*) FROM course_type WHERE LOWER(course_type_code) = LOWER(:code)", nativeQuery = true)
  long countWithCode(@Param("code") String code);

  @Query(value = "SELECT COUNT(*) FROM course_type WHERE LOWER(course_type_code) = LOWER(:code) AND course_type_uuid <> :id",
      nativeQuery = true)
  long countWithCodeExcludingId(@Param("code") String code, @Param("id") UUID id);

}
