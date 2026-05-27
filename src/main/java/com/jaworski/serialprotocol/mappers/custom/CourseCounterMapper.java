package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;

public class CourseCounterMapper {

  public static CourseCounter toEntity(CourseCounterDTO dto) {
    CourseCounter courseCounter = new CourseCounter();
    courseCounter.setUuid(dto.uuid());
    courseCounter.setCounter(dto.counter());
    // Image is resolved and set by the service layer — not mapped here
    return courseCounter;
  }

  public static CourseCounterDTO toDTO(CourseCounter entity) {
    return new CourseCounterDTO(
            entity.getUuid(),
            entity.getCounter(),
            entity.getImage() == null
                    ? null
                    : entity.getImage().getId()
    );
  }
}
