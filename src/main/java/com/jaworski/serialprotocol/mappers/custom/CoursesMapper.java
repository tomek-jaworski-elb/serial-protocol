package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CoursesMapper {

  private CoursesMapper() {
  }

  public static CoursesDTO mapToDTO(Courses courses) {
    if (courses == null) {
      return null;
    }

    CoursesDTO dto = new CoursesDTO();
    dto.setUuid(courses.getUuid());
    dto.setId(courses.getId());
    dto.setParticipantUuid(courses.getParticipant() == null
            ? null
            : courses.getParticipant().getUuid());

    dto.setCourseTypeId(courses.getCourseType() == null
            ? null
            : courses.getCourseType().getId());
    CourseCounter courseCounter = courses.getCourseCounter();
    dto.setCourseCounterUuid(courseCounter == null ? null : courseCounter.getUuid());
    dto.setCounter(courseCounter == null ? null : courseCounter.getCounter());
    dto.setStartDate(courses.getStartDate());
    dto.setEndDate(courses.getEndDate());

    Set<UUID> trainerIds = courses.getTrainers() == null
            ? new HashSet<>()
            : courses.getTrainers().stream()
            .map(Trainer::getUuid)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    dto.setTrainerIds(trainerIds);

    Set<UUID> lecturerIds = courses.getLecturers() == null
            ? new HashSet<>()
            : courses.getLecturers().stream()
            .map(Lecturer::getUuid)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    dto.setLecturerIds(lecturerIds);

    Set<UUID> technicianIds = courses.getTechnicians() == null
            ? new HashSet<>()
            : courses.getTechnicians().stream()
            .map(Technician::getUuid)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    dto.setTechnicianIds(technicianIds);
    return dto;
  }

  /**
   * @deprecated This method creates stub entities with only IDs populated.
   * Persisting the result via JPA {@code save()} would overwrite existing entity data with nulls.
   * Use {@code EntityRepository.getReferenceById()} for JPA-managed references instead.
   * In the current codebase, {@link com.jaworski.serialprotocol.service.db.custom.CoursesService#buildCourses}
   * uses the correct approach. This method is kept for read-only/DTO conversion scenarios only.
   */
  @Deprecated(since = "1.9", forRemoval = false)
  public static Courses mapToEntity(CoursesDTO dto) {
    if (dto == null) {
      return null;
    }

    Courses courses = new Courses();
    courses.setUuid(dto.getUuid());
    courses.setId(dto.getId());
    courses.setStartDate(dto.getStartDate());
    courses.setEndDate(dto.getEndDate());

    if (dto.getParticipantUuid() != null) {
      ParticipantDTO participantDTO = new ParticipantDTO();
      participantDTO.setParticipantUuid(dto.getParticipantUuid());
      courses.setParticipant(ParticipantMapper.mapToEntity(participantDTO));
    }

    if (dto.getCourseTypeId() != null) {
      CourseTypeDTO courseTypeDTO = new CourseTypeDTO();
      courseTypeDTO.setId(dto.getCourseTypeId());
      CourseType courseType = CourseTypeMapper.mapToEntity(courseTypeDTO);
      courseType.setId(dto.getCourseTypeId());
      courses.setCourseType(courseType);
    }

    Set<Trainer> trainers = dto.getTrainerIds() == null
            ? new HashSet<>()
            : dto.getTrainerIds().stream()
            .map(id -> {
              TrainerDTO trainerDTO = new TrainerDTO();
              trainerDTO.setId(id);
              return TrainerMapper.mapToEntity(trainerDTO);
            }).collect(Collectors.toSet());
    courses.setTrainers(trainers);

    Set<Lecturer> lecturers = dto.getLecturerIds() == null
            ? new HashSet<>()
            : dto.getLecturerIds().stream()
            .map(id -> {
              LecturerDTO lecturerDTO = new LecturerDTO();
              lecturerDTO.setId(id);
              return LecturerMapper.mapToEntity(lecturerDTO);
             }).collect(Collectors.toSet());
    courses.setLecturers(lecturers);

    Set<Technician> technicians = dto.getTechnicianIds() == null
            ? new HashSet<>()
            : dto.getTechnicianIds().stream()
            .map(id -> {
              TechnicianDTO technicianDTO = new TechnicianDTO();
              technicianDTO.setId(id);
              return TechnicianMapper.mapToEntity(technicianDTO);
             }).collect(Collectors.toSet());
    courses.setTechnicians(technicians);

    if (dto.getCourseCounterUuid() != null || dto.getCounter() != null) {
      CourseCounter courseCounter = new CourseCounter();
      courseCounter.setUuid(dto.getCourseCounterUuid());
      courseCounter.setCounter(dto.getCounter());
      courses.setCourseCounter(courseCounter);
    }

    return courses;
  }
}

