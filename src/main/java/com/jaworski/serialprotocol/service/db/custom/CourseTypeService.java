package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.mappers.custom.CourseTypeMapper;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class CourseTypeService {

  private final CourseTypeRepository courseTypeRepository;
  private final CoursesRepository coursesRepository;
  private final SoftDeleteSupport softDeleteSupport;

  public CourseTypeDTO save(CourseTypeDTO courseTypeDTO) {
    String code = courseTypeDTO.getCode();
    if (courseTypeRepository.existsByCode(code)) {
      throw new IllegalArgumentException("Course type with code '" + code + "' already exists.");
    }
    if (softDeleteSupport.withDeleted(() -> courseTypeRepository.existsByCode(code))) {
      throw new IllegalArgumentException(softDeleteSupport.heldByDeletedRecord("Course type code '" + code + "'"));
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
        .orElseThrow(() -> softDeleteSupport.notFound("Course type", id));
  }

  public void deleteById(Long id) {
    if (coursesRepository.existsByCourseType_Id(id)) {
      throw new IllegalStateException("This course type is used in existing courses and cannot be deleted.");
    }
    courseTypeRepository.findById(id).ifPresent(courseType -> {
      softDeleteSupport.softDelete(courseType, courseTypeRepository);
    });
  }

  public CourseTypeDTO update(CourseTypeDTO courseTypeDTO) {
    if (courseTypeDTO.getId() == null) {
      throw new IllegalArgumentException("Course type id is required for update");
    }
    CourseType current = courseTypeRepository.findById(courseTypeDTO.getId())
        .orElseThrow(() -> softDeleteSupport.notFound("Course type", courseTypeDTO.getId()));
    VersionGuard.check(CourseType.class, courseTypeDTO.getId(), current.getVersion(), courseTypeDTO.getVersion());
    if (courseTypeRepository.existsByCodeAndIdNot(courseTypeDTO.getCode(), courseTypeDTO.getId())) {
      throw new IllegalArgumentException("Course type with code '" + courseTypeDTO.getCode() + "' already exists.");
    }
    if (softDeleteSupport.withDeleted(
        () -> courseTypeRepository.existsByCodeAndIdNot(courseTypeDTO.getCode(), courseTypeDTO.getId()))) {
      throw new IllegalArgumentException(
          softDeleteSupport.heldByDeletedRecord("Course type code '" + courseTypeDTO.getCode() + "'"));
    }
    var entity = CourseTypeMapper.mapToEntity(courseTypeDTO);
    return CourseTypeMapper.mapToDTO(courseTypeRepository.saveAndFlush(entity));
  }
}
