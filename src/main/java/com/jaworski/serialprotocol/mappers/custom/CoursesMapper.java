package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.Trainer;

import java.util.HashSet;
import java.util.Set;
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
    dto.setParticipantUuid(courses.getParticipant() == null ? null : courses.getParticipant().getUuid());
    dto.setCourseTypeId(courses.getCourseType() == null ? null : courses.getCourseType().getId());
    dto.setStartDate(courses.getStartDate());
    dto.setEndDate(courses.getEndDate());

    Set<Long> trainerIds = courses.getTrainers() == null
        ? new HashSet<>()
        : courses.getTrainers().stream().map(Trainer::getId).collect(Collectors.toSet());
    dto.setTrainerIds(trainerIds);

    Set<Long> lecturerIds = courses.getLecturers() == null
        ? new HashSet<>()
        : courses.getLecturers().stream().map(Lecturer::getLecturerId).collect(Collectors.toSet());
    dto.setLecturerIds(lecturerIds);

    return dto;
  }

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
      Participant participant = new Participant();
      participant.setUuid(dto.getParticipantUuid());
      courses.setParticipant(participant);
    }

    if (dto.getCourseTypeId() != null) {
      CourseType courseType = new CourseType();
      courseType.setId(dto.getCourseTypeId());
      courses.setCourseType(courseType);
    }

    Set<Trainer> trainers = dto.getTrainerIds() == null
        ? new HashSet<>()
        : dto.getTrainerIds().stream().map(id -> {
          Trainer trainer = new Trainer();
          trainer.setId(id);
          return trainer;
        }).collect(Collectors.toSet());
    courses.setTrainers(trainers);

    Set<Lecturer> lecturers = dto.getLecturerIds() == null
        ? new HashSet<>()
        : dto.getLecturerIds().stream().map(id -> {
          Lecturer lecturer = new Lecturer();
          lecturer.setLecturerId(id);
          return lecturer;
        }).collect(Collectors.toSet());
    courses.setLecturers(lecturers);

    return courses;
  }
}

