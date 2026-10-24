package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseTypeRepository extends JpaRepository<CourseType, Long> {

  // Case-insensitive on purpose: the MariaDB default collation already treats "abc" and "ABC"
  // as the same unique value, so the pre-check has to agree with the constraint (H2 is case-sensitive).
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

}
