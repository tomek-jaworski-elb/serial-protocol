package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseTypeRepository extends JpaRepository<CourseType, Long> {

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, Long id);

}
