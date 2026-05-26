package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.mappers.custom.CourseCounterMapper;
import com.jaworski.serialprotocol.repository.custom.CourseCounterRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseCounterService {

  private final CourseCounterRepository courseCounterRepository;
  private final ImageRepository imageRepository;

  public CourseCounterDTO save(CourseCounterDTO courseCounterDTO) {
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
    return courseCounterRepository.findMaxCounter() + 1;
  }

  private Image resolveImage(UUID imageId) {
    if (imageId == null) {
      return null;
    }
    return imageRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image with id " + imageId + " not found"));
  }

  public void delete(UUID uuid) {
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

  public CourseCounterDTO update(CourseCounterDTO toSave) {
    CourseCounter courseCounter = courseCounterRepository.findById(toSave.uuid())
            .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + toSave.uuid() + " not found"));
    if (courseCounter.getImage() != null && courseCounter.getImage().getId() != null
            && toSave.imageUuid() != null
            && !courseCounter.getImage().getId().equals(toSave.imageUuid())) {
      UUID id = courseCounter.getImage().getId();
      imageRepository.deleteById(id);
    }
    courseCounter.setImage(resolveImage(toSave.imageUuid()));
    courseCounter.setCounter(toSave.counter());
    CourseCounter save = courseCounterRepository.save(courseCounter);
    return CourseCounterMapper.toDTO(save);
  }
}
