package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.mappers.custom.CourseTypeMapper;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CourseTypeService {

  private final CourseTypeRepository courseTypeRepository;

  public CourseTypeDTO save(CourseTypeDTO courseTypeDTO) {
    var entity = CourseTypeMapper.mapToEntity(courseTypeDTO);
    return CourseTypeMapper.mapToDTO(courseTypeRepository.save(entity));
  }

  public List<CourseTypeDTO> findAll() {
    return courseTypeRepository.findAll().stream()
        .map(CourseTypeMapper::mapToDTO)
        .toList();
  }

  public CourseTypeDTO findById(Long id) {
    return courseTypeRepository.findById(id)
        .map(CourseTypeMapper::mapToDTO)
        .orElseThrow(() -> new IllegalArgumentException("Course type with id " + id + " not found"));
  }

  public void deleteById(Long id) {
    courseTypeRepository.deleteById(id);
  }

  public CourseTypeDTO update(CourseTypeDTO courseTypeDTO) {
    if (courseTypeDTO.getId() == null) {
      throw new IllegalArgumentException("Course type id is required for update");
    }
    if (!courseTypeRepository.existsById(courseTypeDTO.getId())) {
      throw new IllegalArgumentException("Course type with id " + courseTypeDTO.getId() + " not found");
    }
    return save(courseTypeDTO);
  }
}
