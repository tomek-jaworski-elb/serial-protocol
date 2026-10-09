package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.repository.custom.CourseCounterRepository;
import com.jaworski.serialprotocol.mappers.custom.CoursesMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  private final CourseCounterRepository courseCounterRepository;
  private final TrainerRepository trainerRepository;
  private final LecturerRepository lecturerRepository;
  private final TechnicianRepository technicianRepository;
  private final SoftDeleteSupport softDeleteSupport;

  @PersistenceContext
  private EntityManager entityManager;

  @Transactional(readOnly = true)
  public List<CoursesDTO> findAll() {
    return coursesRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
        .map(CoursesMapper::mapToDTO)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<CoursesDTO> findAll(Pageable pageable) {
    return coursesRepository.findAll(pageable).map(CoursesMapper::mapToDTO);
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

  @Transactional(readOnly = true)
  public List<CoursesDTO> findByCourseCounterUuid(UUID courseCounterUuid) {
    return coursesRepository.findByCourseCounter_Uuid(courseCounterUuid).stream()
        .map(CoursesMapper::mapToDTO)
        .toList();
  }

  public Long nextId() {
    // Deleted courses count: their ids are never handed out again.
    return softDeleteSupport.withDeleted(coursesRepository::findMaxCoursesId) + 1;
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
    coursesRepository.findById(uuid).ifPresent(course -> {
      softDeleteSupport.softDelete(course, coursesRepository);
      LOGGER.info("Deleted course with uuid={}", uuid);
    });
  }

  public CoursesDTO update(CoursesDTO dto) {
    if (dto.getUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    Courses current = coursesRepository.findById(dto.getUuid())
        .orElseThrow(() -> softDeleteSupport.notFound("Course", dto.getUuid()));
    VersionGuard.check(Courses.class, dto.getUuid(), current.getVersion(), dto.getVersion());
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("Participant UUID is required");
    }
    if (dto.getCourseTypeId() == null) {
      throw new IllegalArgumentException("Course type ID is required");
    }
    validateDates(dto);
    Courses courses = buildCourses(dto);
    Courses updated = coursesRepository.saveAndFlush(courses);
    LOGGER.info("Updated course with uuid={}", updated.getUuid());
    return CoursesMapper.mapToDTO(updated);
  }

  /**
   * A course may only point at active rows, and they must still be active when it is saved. The parent is
   * loaded (so a deleted one is simply not found) under an optimistic lock: if someone deletes it before this
   * transaction commits, its version has moved and the commit fails instead of leaving a course that points
   * at a hidden row. {@code getReferenceById} would do neither, because it never hits the database.
   */
  private <T extends SoftDeletable> T activeParent(Class<T> type, Object id, String what) {
    T parent = id == null ? null : entityManager.find(type, id);
    if (parent == null) {
      LOGGER.warn("{} {} does not exist or has been deleted", type.getSimpleName(), id);
      throw new IllegalArgumentException(what + " does not exist or has been deleted.");
    }
    // lock() after find(): a lock mode passed to find() is applied to everything loaded with the parent,
    // and a photo loaded with a participant or trainer has no version to lock.
    entityManager.lock(parent, LockModeType.OPTIMISTIC);
    return parent;
  }

  private Courses buildCourses(CoursesDTO dto) {
    Participant participant = activeParent(Participant.class, dto.getParticipantUuid(), "The selected participant");
    CourseType courseType = activeParent(CourseType.class, dto.getCourseTypeId(), "The selected course type");

    Set<Trainer> trainers = dto.getTrainerIds() == null
        ? new HashSet<>()
        : dto.getTrainerIds().stream()
        .map(id -> activeParent(Trainer.class, id, "One of the selected trainers"))
        .collect(Collectors.toSet());

    Set<Lecturer> lecturers = dto.getLecturerIds() == null
        ? new HashSet<>()
        : dto.getLecturerIds().stream()
        .map(id -> activeParent(Lecturer.class, id, "One of the selected lecturers"))
        .collect(Collectors.toSet());

    Set<Technician> technicians = dto.getTechnicianIds() == null
        ? new HashSet<>()
        : dto.getTechnicianIds().stream()
        .map(id -> activeParent(Technician.class, id, "One of the selected technicians"))
        .collect(Collectors.toSet());

    CourseCounter courseCounter = resolveCourseCounter(dto);

    Courses courses = new Courses();
    courses.setUuid(dto.getUuid());
    courses.setId(dto.getId());
    courses.setVersion(dto.getVersion());
    courses.setParticipant(participant);
    courses.setCourseType(courseType);
    courses.setCourseCounter(courseCounter);
    courses.setStartDate(dto.getStartDate());
    courses.setEndDate(dto.getEndDate());
    courses.setTrainers(trainers);
    courses.setLecturers(lecturers);
    courses.setTechnicians(technicians);
    return courses;
  }

  private CourseCounter resolveCourseCounter(CoursesDTO dto) {
    if (dto.getCourseCounterUuid() != null) {
      return activeParent(CourseCounter.class, dto.getCourseCounterUuid(), "The selected course counter");
    }
    if (dto.getCounter() != null) {
      CourseCounter byNumber = courseCounterRepository.findByCounter(dto.getCounter())
          .orElseThrow(() -> missingCounter(dto.getCounter()));
      entityManager.lock(byNumber, LockModeType.OPTIMISTIC);
      return byNumber;
    }
    return null;
  }

  /** The number came from the form, so it may be echoed back; a counter in the trash is named as such. */
  private IllegalArgumentException missingCounter(Long counter) {
    boolean inTrash = softDeleteSupport.withDeleted(() -> courseCounterRepository.existsByCounter(counter));
    return new IllegalArgumentException(inTrash
        ? "Course counter " + counter + " has been deleted. Ask an administrator to restore it."
        : "Course counter " + counter + " does not exist.");
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
