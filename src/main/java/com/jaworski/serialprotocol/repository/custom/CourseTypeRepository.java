package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CourseTypeRepository extends JpaRepository<CourseType, UUID> {

  // Case-insensitive on purpose: the MariaDB default collation already treats "abc" and "ABC"
  // as the same unique value, so the pre-check has to agree with the constraint (H2 is case-sensitive).
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

}
