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
  private final ImageService imageService;
  private final IdAllocator idAllocator;
  private final CoursesRepository coursesRepository;

  public CourseCounterDTO save(CourseCounterDTO courseCounterDTO) {
    if (courseCounterDTO.counter() == null) {
      courseCounterDTO = new CourseCounterDTO(courseCounterDTO.uuid(),
          idAllocator.next(IdAllocator.COURSE_COUNTER, courseCounterRepository::findMaxCounter),
          courseCounterDTO.imageUuid());
    }
    // save() with a uuid is an update through the merge path (older callers do that), so the
    // record's own number must not count as a duplicate.
    long clashes = courseCounterDTO.uuid() == null
        ? courseCounterRepository.countWithCounter(courseCounterDTO.counter())
        : courseCounterRepository.countWithCounterExcludingUuid(courseCounterDTO.counter(), courseCounterDTO.uuid());
    if (clashes > 0) {
      throw new IllegalArgumentException("Course counter " + courseCounterDTO.counter() + " already exists");
    }
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

  /** The number the next counter would get; a hint, never a value to submit. */
  @Transactional(readOnly = true)
  public Long nextCounter() {
    return idAllocator.peek(IdAllocator.COURSE_COUNTER, courseCounterRepository::findMaxCounter);
  }

  /** Creates the counter and stores the uploaded photo, if any, in the same transaction. */
  public CourseCounterDTO save(CourseCounterDTO dto, UploadedImage upload) {
    UUID image = upload == null
        ? dto.imageUuid()
        : imageService.saveImage(upload.data(), upload.contentType()).getId();
    return save(new CourseCounterDTO(dto.uuid(), dto.counter(), image, dto.version()));
  }

  /**
   * Updates the counter together with its photo. Same precedence as for participants: an
   * uploaded file wins, then an explicit remove, and only a request that asks for neither
   * keeps what is there.
   */
  public CourseCounterDTO update(CourseCounterDTO dto, UploadedImage upload, boolean removeImage) {
    if (dto.uuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    UUID image;
    if (upload != null) {
      image = imageService.saveImage(upload.data(), upload.contentType()).getId();
    } else if (removeImage) {
      image = null;
    } else {
      CourseCounter existing = courseCounterRepository.findById(dto.uuid())
          .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + dto.uuid() + " not found"));
      image = existing.getImage() == null ? null : existing.getImage().getId();
    }
    return update(new CourseCounterDTO(dto.uuid(), dto.counter(), image, dto.version()));
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
      throw new IllegalStateException("Cannot delete course counter with uuid " + uuid + " because it is referenced by existing courses.");
    }
    CourseCounter courseCounter = courseCounterRepository.findById(uuid)
            .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + uuid + " not found"));
    Image image = courseCounter.getImage();
    if (image != null) {
      imageRepository.delete(image);
    }
    courseCounterRepository.deleteById(uuid);
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
            .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + toSave.uuid() + " not found"));
    OptimisticLock.check(toSave.version(), courseCounter.getVersion(), "Course counter");
    if (toSave.counter() != null
        && courseCounterRepository.countWithCounterExcludingUuid(toSave.counter(), toSave.uuid()) > 0) {
      throw new IllegalArgumentException("Course counter " + toSave.counter() + " is already used by another record");
    }
    UUID oldImageId = courseCounter.getImage() != null ? courseCounter.getImage().getId() : null;
    courseCounter.setImage(resolveImage(toSave.imageUuid()));
    courseCounter.setCounter(toSave.counter());
    CourseCounter save = courseCounterRepository.save(courseCounter);
    if (oldImageId != null && !oldImageId.equals(toSave.imageUuid())) {
      imageRepository.deleteById(oldImageId);
    }
    return CourseCounterMapper.toDTO(save);
  }
}
