package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.mappers.custom.ParticipantMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ParticipantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantService.class);
  private final ParticipantRepository participantRepository;
  private final ImageRepository imageRepository;
  private final CoursesRepository coursesRepository;
  private final SoftDeleteSupport softDeleteSupport;

  @Transactional(readOnly = true)
  public List<ParticipantDTO> findAll() {
    return participantRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
            .map(ParticipantMapper::mapToDTO)
            .toList();
  }

  @Transactional(readOnly = true)
  public Page<ParticipantDTO> findAll(Pageable pageable) {
    return participantRepository.findAll(pageable).map(ParticipantMapper::mapToDTO);
  }

  @Transactional(readOnly = true)
  public ParticipantDTO findByUuid(UUID uuid) {
    return participantRepository.findById(uuid)
            .map(ParticipantMapper::mapToDTO)
            .orElse(null);
  }

  public Long nextId() {
    // Deleted participants count: their ids are never handed out again.
    return softDeleteSupport.withDeleted(participantRepository::findMaxParticipantId) + 1;
  }

  public boolean isIdTaken(Long id) {
    return participantRepository.countWithParticipantId(id) > 0;
  }

  public boolean isIdTakenByOther(Long id, UUID uuid) {
    return participantRepository.countWithParticipantIdExcludingUuid(id, uuid) > 0;
  }

  public ParticipantDTO save(ParticipantDTO dto) {
    if (dto.getId() == null) {
      dto.setId(nextId());
    }
    Long id = dto.getId();
    if (isIdTaken(id)) {
      throw new IllegalArgumentException("Participant with id " + id + " already exists");
    }
    if (softDeleteSupport.withDeleted(() -> participantRepository.countWithParticipantId(id)) > 0) {
      throw new IllegalArgumentException(softDeleteSupport.heldByDeletedRecord("Participant id " + id));
    }

    Image saveImage = resolveImage(dto.getImage());
    Participant participant = ParticipantMapper.mapToEntity(dto);
    participant.setImage(saveImage);
    Participant saved = participantRepository.save(participant);
    return ParticipantMapper.mapToDTO(saved);
  }

  public void deleteByUuid(UUID uuid) {
    if (coursesRepository.existsByParticipant_Uuid(uuid)) {
      throw new IllegalStateException("This participant is used in existing courses and cannot be deleted.");
    }
    // Marked, not removed: the photo stays with the row so an administrator can restore it.
    participantRepository.findById(uuid).ifPresent(participant -> {
      softDeleteSupport.softDelete(participant, participantRepository);
    });
  }

  @Transactional
  public ParticipantDTO updateByUuid(ParticipantDTO dto) {
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    Participant current = participantRepository.findById(dto.getParticipantUuid())
        .orElseThrow(() -> softDeleteSupport.notFound("Participant", dto.getParticipantUuid()));
    VersionGuard.check(Participant.class, dto.getParticipantUuid(), current.getVersion(), dto.getVersion());
    if (dto.getId() != null && isIdTakenByOther(dto.getId(), dto.getParticipantUuid())) {
      throw new IllegalArgumentException("Participant id " + dto.getId() + " is already used by another participant");
    }
    if (dto.getId() != null && softDeleteSupport.withDeleted(
        () -> participantRepository.countWithParticipantIdExcludingUuid(dto.getId(), dto.getParticipantUuid())) > 0) {
      throw new IllegalArgumentException(softDeleteSupport.heldByDeletedRecord("Participant id " + dto.getId()));
    }

    // Remember old image UUID before any changes
    UUID oldImageId = current.getImage() != null ? current.getImage().getId() : null;

    Image requestedImage = resolveImage(dto.getImage());

    // First: save participant with new image to update the FK
    Participant participant = ParticipantMapper.mapToEntity(dto);
    participant.setImage(requestedImage);
    Participant updated = participantRepository.saveAndFlush(participant);

    // Then: delete old image if it was replaced
    UUID newImageId = requestedImage != null ? requestedImage.getId() : null;
    if (oldImageId != null && !oldImageId.equals(newImageId)) {
      imageRepository.deleteById(oldImageId);
    }

    return ParticipantMapper.mapToDTO(updated);
  }

  private Image resolveImage(UUID imageId) {
    if (imageId == null) {
      return null;
    }
    return imageRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image with id " + imageId + " not found"));
  }
}

