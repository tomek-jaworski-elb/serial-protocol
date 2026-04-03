package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.Image;

public class CourseCounterMapper {

  public static CourseCounter toEntity(CourseCounterDTO dto) {
    CourseCounter courseCounter = new CourseCounter();
    courseCounter.setUuid(dto.uuid());
    courseCounter.setCounter(dto.counter());
    if (dto.imageUuid() != null) {
      Image image = new Image();
      image.setId(dto.imageUuid());
      courseCounter.setImage(image);
    } else {
      courseCounter.setImage(null);
    }
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
