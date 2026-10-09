package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.mappers.custom.CourseCounterMapper;
import com.jaworski.serialprotocol.repository.custom.CourseCounterRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseCounterService {

  private final CourseCounterRepository courseCounterRepository;
  private final ImageRepository imageRepository;
  private final CoursesRepository coursesRepository;
  private final SoftDeleteSupport softDeleteSupport;

  public CourseCounterDTO save(CourseCounterDTO courseCounterDTO) {
    if (courseCounterDTO.uuid() != null) {
      throw new IllegalArgumentException("A new course counter cannot carry a uuid; use update for an existing one.");
    }
    requireCounterFree(courseCounterDTO.counter(), null);
    CourseCounter entity = CourseCounterMapper.toEntity(courseCounterDTO);
    UUID uuid = courseCounterDTO.imageUuid();
    Image image = resolveImage(uuid);
    entity.setImage(image);
    entity = courseCounterRepository.save(entity);
    return CourseCounterMapper.toDTO(entity);
  }

  public List<CourseCounterDTO> findAll() {
    return courseCounterRepository.findAll(Sort.by(Sort.Direction.DESC, "counter")).stream()
            .map(CourseCounterMapper::toDTO)
            .toList();
  }

  public Page<CourseCounterDTO> findAll(Pageable pageable) {
    return courseCounterRepository.findAll(pageable).map(CourseCounterMapper::toDTO);
  }

  public Long nextCounter() {
    // Deleted counters count: their numbers are never handed out again.
    return softDeleteSupport.withDeleted(courseCounterRepository::findMaxCounter) + 1;
  }

  /**
   * A counter value is unique across all rows, deleted ones included, so a taken value is reported
   * differently depending on who holds it. {@code ownUuid} is the row being edited, or null for a new one.
   */
  private void requireCounterFree(Long counter, UUID ownUuid) {
    if (counter == null) {
      return;
    }
    // exists-queries on purpose: loading a deleted holder would leave it in the session cache
    if (takenByOther(counter, ownUuid)) {
      throw new IllegalArgumentException("Course counter " + counter + " already exists.");
    }
    if (softDeleteSupport.withDeleted(() -> takenByOther(counter, ownUuid))) {
      throw new IllegalArgumentException(softDeleteSupport.heldByDeletedRecord("Course counter " + counter));
    }
  }

  private boolean takenByOther(Long counter, UUID ownUuid) {
    return ownUuid == null
        ? courseCounterRepository.existsByCounter(counter)
        : courseCounterRepository.existsByCounterAndUuidNot(counter, ownUuid);
  }

  private Image resolveImage(UUID imageId) {
    if (imageId == null) {
      return null;
    }
    return imageRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image with id " + imageId + " not found"));
  }

  public void delete(UUID uuid) {
    if (coursesRepository.existsByCourseCounter_Uuid(uuid)) {
      throw new IllegalStateException("This course counter is used in existing courses and cannot be deleted.");
    }
    CourseCounter courseCounter = courseCounterRepository.findById(uuid)
            .orElseThrow(() -> softDeleteSupport.notFound("Course counter", uuid));
    // Marked, not removed: the photo stays with the row so an administrator can restore it.
    softDeleteSupport.softDelete(courseCounter, courseCounterRepository);
  }

  public Optional<CourseCounterDTO> getByUuid(UUID uuid) {
    return courseCounterRepository.findById(uuid)
            .map(CourseCounterMapper::toDTO);
  }

  public List<CourseCounterDTO> findAllByUuids(Collection<UUID> uuids) {
    return courseCounterRepository.findAllByUuidIn(uuids).stream()
            .map(CourseCounterMapper::toDTO)
            .toList();
  }

  public CourseCounterDTO update(CourseCounterDTO toSave) {
    CourseCounter courseCounter = courseCounterRepository.findById(toSave.uuid())
            .orElseThrow(() -> softDeleteSupport.notFound("Course counter", toSave.uuid()));
    VersionGuard.check(CourseCounter.class, toSave.uuid(), courseCounter.getVersion(), toSave.version());
    requireCounterFree(toSave.counter(), toSave.uuid());
    UUID oldImageId = courseCounter.getImage() != null ? courseCounter.getImage().getId() : null;
    courseCounter.setImage(resolveImage(toSave.imageUuid()));
    courseCounter.setCounter(toSave.counter());
    CourseCounter save = courseCounterRepository.saveAndFlush(courseCounter);
    if (oldImageId != null && !oldImageId.equals(toSave.imageUuid())) {
      imageRepository.deleteById(oldImageId);
    }
    return CourseCounterMapper.toDTO(save);
  }
}
