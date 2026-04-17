package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.mappers.custom.TechnicianMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TechnicianService {

  private final ImageRepository imageRepository;
  private final TechnicianRepository technicianRepository;
  private final CoursesRepository coursesRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(TechnicianService.class);

  @Transactional(readOnly = true)
  public List<TechnicianDTO> findAll() {
    return technicianRepository.findAll().stream()
            .map(TechnicianMapper::mapToDTO)
            .toList();
  }

  public TechnicianDTO findById(UUID id) {
    return technicianRepository.findById(id)
            .map(TechnicianMapper::mapToDTO)
            .orElse(null);
  }

  public TechnicianDTO save(TechnicianDTO dto) {
    Technician technician = TechnicianMapper.mapToEntity(dto);
    technician.setImages(resolveImages(dto.getImagesUuid()));
    Technician saved = technicianRepository.save(technician);
    return TechnicianMapper.mapToDTO(saved);
  }

  public void deleteById(UUID id) {
    if (coursesRepository.existsByTechnicians_Uuid(id)) {
      throw new IllegalStateException("Cannot delete technician with id " + id + " because it is referenced by existing courses.");
    }
    technicianRepository.findById(id).ifPresent(technician -> {
      if (!technician.getImages().isEmpty()) {
        imageRepository.deleteAll(technician.getImages());
      }
    });
    technicianRepository.deleteById(id);
  }

  public TechnicianDTO updateById(TechnicianDTO dto) {
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Technician id is required for update");
    }
    Technician existing = technicianRepository.findById(dto.getId())
        .orElseThrow(() -> new IllegalArgumentException("Technician with id " + dto.getId() + " not found"));

    Set<Image> previousImages = new HashSet<>(existing.getImages());
    Set<Image> requestedImages = resolveImages(dto.getImagesUuid());

    existing.setName(dto.getName());
    existing.setSurname(dto.getSurname());
    existing.setEmail(dto.getEmail());
    existing.setNickname(dto.getNickname());
    existing.setImages(requestedImages);

    Technician updated = technicianRepository.save(existing);

    Set<Image> imagesToDelete = previousImages.stream()
        .filter(image -> !requestedImages.contains(image))
        .collect(java.util.stream.Collectors.toSet());
    if (!imagesToDelete.isEmpty()) {
      imageRepository.deleteAll(imagesToDelete);
    }
    return TechnicianMapper.mapToDTO(updated);
  }

  private Set<Image> resolveImages(Set<UUID> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return new HashSet<>();
    }
    List<Image> images = imageRepository.findAllById(imageIds);
    if (images.size() != imageIds.size()) {
      throw new IllegalArgumentException("One or more image ids do not exist");
    }
    return new HashSet<>(images);
  }
}

