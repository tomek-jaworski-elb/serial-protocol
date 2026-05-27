package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.mappers.custom.CourseTypeMapper;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CourseTypeService {

  private final CourseTypeRepository courseTypeRepository;
  private final CoursesRepository coursesRepository;

  public CourseTypeDTO save(CourseTypeDTO courseTypeDTO) {
    if (courseTypeRepository.existsByCode(courseTypeDTO.getCode())) {
      throw new IllegalArgumentException("Course type with code '" + courseTypeDTO.getCode() + "' already exists.");
    }
    var entity = CourseTypeMapper.mapToEntity(courseTypeDTO);
    return CourseTypeMapper.mapToDTO(courseTypeRepository.save(entity));
  }

  public List<CourseTypeDTO> findAll() {
    return courseTypeRepository.findAll().stream()
        .map(CourseTypeMapper::mapToDTO)
        .toList();
  }

  public Page<CourseTypeDTO> findAll(Pageable pageable) {
    return courseTypeRepository.findAll(pageable).map(CourseTypeMapper::mapToDTO);
  }

  public CourseTypeDTO findById(Long id) {
    return courseTypeRepository.findById(id)
        .map(CourseTypeMapper::mapToDTO)
        .orElseThrow(() -> new IllegalArgumentException("Course type with id " + id + " not found"));
  }

  public void deleteById(Long id) {
    if (coursesRepository.existsByCourseType_Id(id)) {
      throw new IllegalStateException("Cannot delete course type with id " + id + " because it is referenced by existing courses.");
    }
    courseTypeRepository.deleteById(id);
  }

  public CourseTypeDTO update(CourseTypeDTO courseTypeDTO) {
    if (courseTypeDTO.getId() == null) {
      throw new IllegalArgumentException("Course type id is required for update");
    }
    if (!courseTypeRepository.existsById(courseTypeDTO.getId())) {
      throw new IllegalArgumentException("Course type with id " + courseTypeDTO.getId() + " not found");
    }
    if (courseTypeRepository.existsByCodeAndIdNot(courseTypeDTO.getCode(), courseTypeDTO.getId())) {
      throw new IllegalArgumentException("Course type with code '" + courseTypeDTO.getCode() + "' already exists.");
    }
    var entity = CourseTypeMapper.mapToEntity(courseTypeDTO);
    return CourseTypeMapper.mapToDTO(courseTypeRepository.save(entity));
  }
}
