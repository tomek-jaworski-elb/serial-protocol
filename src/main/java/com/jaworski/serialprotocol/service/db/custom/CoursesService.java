package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.mappers.custom.CoursesMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CoursesService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CoursesService.class);
  private final CoursesRepository coursesRepository;
  private final ParticipantRepository participantRepository;
  private final CourseTypeRepository courseTypeRepository;
  private final TrainerRepository trainerRepository;
  private final LecturerRepository lecturerRepository;

  @Transactional(readOnly = true)
  public List<CoursesDTO> findAll() {
    return coursesRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
        .map(CoursesMapper::mapToDTO)
        .toList();
  }

  @Transactional(readOnly = true)
  public CoursesDTO findByUuid(UUID uuid) {
    return coursesRepository.findById(uuid)
        .map(CoursesMapper::mapToDTO)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public List<CoursesDTO> findByParticipantUuid(UUID participantUuid) {
    return coursesRepository.findByParticipant_Uuid(participantUuid).stream()
        .map(CoursesMapper::mapToDTO)
        .toList();
  }

  public Long nextId() {
    return coursesRepository.findMaxCoursesId() + 1;
  }

  public CoursesDTO save(CoursesDTO dto) {
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("Participant UUID is required");
    }
    if (dto.getCourseTypeId() == null) {
      throw new IllegalArgumentException("Course type ID is required");
    }
    validateDates(dto);
    if (dto.getId() == null) {
      dto.setId(nextId());
    }
    Courses courses = buildCourses(dto);
    Courses saved = coursesRepository.save(courses);
    LOGGER.info("Saved course with uuid={}", saved.getUuid());
    return CoursesMapper.mapToDTO(saved);
  }

  public void deleteByUuid(UUID uuid) {
    coursesRepository.deleteById(uuid);
    LOGGER.info("Deleted course with uuid={}", uuid);
  }

  public CoursesDTO update(CoursesDTO dto) {
    if (dto.getUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    if (!coursesRepository.existsById(dto.getUuid())) {
      throw new IllegalArgumentException("Course with UUID " + dto.getUuid() + " not found");
    }
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("Participant UUID is required");
    }
    if (dto.getCourseTypeId() == null) {
      throw new IllegalArgumentException("Course type ID is required");
    }
    validateDates(dto);
    Courses courses = buildCourses(dto);
    Courses updated = coursesRepository.save(courses);
    LOGGER.info("Updated course with uuid={}", updated.getUuid());
    return CoursesMapper.mapToDTO(updated);
  }

  private Courses buildCourses(CoursesDTO dto) {
    Participant participant = participantRepository.getReferenceById(dto.getParticipantUuid());
    CourseType courseType = courseTypeRepository.getReferenceById(dto.getCourseTypeId());

    Set<Trainer> trainers = dto.getTrainerIds() == null
        ? new HashSet<>()
        : dto.getTrainerIds().stream()
        .map(trainerRepository::getReferenceById)
        .collect(Collectors.toSet());

    Set<Lecturer> lecturers = dto.getLecturerIds() == null
        ? new HashSet<>()
        : dto.getLecturerIds().stream()
        .map(lecturerRepository::getReferenceById)
        .collect(Collectors.toSet());

    Courses courses = new Courses();
    courses.setUuid(dto.getUuid());
    courses.setId(dto.getId());
    courses.setParticipant(participant);
    courses.setCourseType(courseType);
    courses.setStartDate(dto.getStartDate());
    courses.setEndDate(dto.getEndDate());
    courses.setTrainers(trainers);
    courses.setLecturers(lecturers);
    return courses;
  }

  private void validateDates(CoursesDTO dto) {
    if (dto.getStartDate() == null || dto.getEndDate() == null) {
      throw new IllegalArgumentException("Start date and end date are required");
    }
    if (dto.getEndDate().isBefore(dto.getStartDate())) {
      throw new IllegalArgumentException(
          "End date (" + dto.getEndDate() + ") must be the same as or after start date (" + dto.getStartDate() + ")");
    }
  }
}
