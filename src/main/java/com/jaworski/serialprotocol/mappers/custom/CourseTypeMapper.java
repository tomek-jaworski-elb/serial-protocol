package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.entity.custom.CourseType;

public class CourseTypeMapper {

  private CourseTypeMapper() {
  }

  public static CourseTypeDTO mapToDTO(CourseType courseType) {
    if (courseType == null) {
      return null;
    }
    CourseTypeDTO dto = new CourseTypeDTO();
    dto.setId(courseType.getId());
    dto.setCode(courseType.getCode());
    dto.setDescription(courseType.getDescription());
    dto.setLongDescription(courseType.getLongDescription());
    return dto;
  }

  public static CourseType mapToEntity(CourseTypeDTO dto) {
    if (dto == null) {
      return null;
    }
    CourseType courseType = new CourseType();
    courseType.setId(dto.getId());
    courseType.setCode(dto.getCode());
    courseType.setDescription(dto.getDescription());
    courseType.setLongDescription(dto.getLongDescription());
    return courseType;
  }
}

