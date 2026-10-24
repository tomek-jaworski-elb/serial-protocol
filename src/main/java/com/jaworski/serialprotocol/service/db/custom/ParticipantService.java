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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ParticipantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantService.class);
  private final ParticipantRepository participantRepository;
  private final ImageRepository imageRepository;
  private final ImageService imageService;
  private final IdAllocator idAllocator;
  private final CoursesRepository coursesRepository;

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

  /** The number the next add would get; a hint for the form, never a value to submit. */
  @Transactional(readOnly = true)
  public Long nextId() {
    return idAllocator.peek(IdAllocator.PARTICIPANT, participantRepository::findMaxParticipantId);
  }

  public boolean isIdTaken(Long id) {
    return participantRepository.countWithParticipantId(id) > 0;
  }

  public boolean isIdTakenByOther(Long id, UUID uuid) {
    return participantRepository.countWithParticipantIdExcludingUuid(id, uuid) > 0;
  }

  public ParticipantDTO save(ParticipantDTO dto) {
    if (dto.getId() == null) {
      // Reserved under a row lock in this transaction, so two forms submitted together
      // cannot get the same number; a number typed by hand is still checked below.
      dto.setId(idAllocator.next(IdAllocator.PARTICIPANT, participantRepository::findMaxParticipantId));
    }
    if (isIdTaken(dto.getId())) {
      throw new IllegalArgumentException("Participant with id " + dto.getId() + " already exists");
    }

    Image saveImage = resolveImage(dto.getImage());
    Participant participant = ParticipantMapper.mapToEntity(dto);
    participant.setImage(saveImage);
    Participant saved = participantRepository.save(participant);
    return ParticipantMapper.mapToDTO(saved);
  }

  public void deleteByUuid(UUID uuid) {
    if (coursesRepository.existsByParticipant_Uuid(uuid)) {
      throw new IllegalStateException("Cannot delete participant with uuid " + uuid + " because they are referenced by existing courses.");
    }
    participantRepository.findById(uuid).ifPresent(participant -> {
      if (participant.getImage() != null) {
        imageRepository.deleteById(participant.getImage().getId());
      }
    });
    participantRepository.deleteById(uuid);
  }

  @Transactional
  public ParticipantDTO updateByUuid(ParticipantDTO dto) {
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    if (dto.getId() != null && isIdTakenByOther(dto.getId(), dto.getParticipantUuid())) {
      throw new IllegalArgumentException("Participant id " + dto.getId() + " is already used by another participant");
    }

    // Load-and-copy rather than merging a detached copy: the version is checked against what is
    // stored, and a record deleted meanwhile is reported instead of silently recreated.
    Participant participant = participantRepository.findById(dto.getParticipantUuid())
            .orElseThrow(() -> new IllegalArgumentException("Participant with uuid " + dto.getParticipantUuid() + " not found"));
    OptimisticLock.check(dto.getVersion(), participant.getVersion(), "Participant");
    UUID oldImageId = participant.getImage() != null ? participant.getImage().getId() : null;

    Image requestedImage = resolveImage(dto.getImage());

    participant.setId(dto.getId());
    participant.setName(dto.getName());
    participant.setSurname(dto.getSurname());
    participant.setNotes(dto.getNotes());
    participant.setNickname(dto.getNickname());
    participant.setEmail(dto.getEmail());
    participant.setPhoneNumber(dto.getPhoneNumber());
    participant.setAddress(dto.getAddress());
    participant.setBirthDate(dto.getBirthDate());
    // First: flush the participant with the new image so the FK moves before the old row goes
    participant.setImage(requestedImage);
    Participant updated = participantRepository.saveAndFlush(participant);

    // Then: delete old image if it was replaced
    UUID newImageId = requestedImage != null ? requestedImage.getId() : null;
    if (oldImageId != null && !oldImageId.equals(newImageId)) {
      imageRepository.deleteById(oldImageId);
    }

    return ParticipantMapper.mapToDTO(updated);
  }

  /** Creates the participant and stores the uploaded photo, if any, in the same transaction. */
  public ParticipantDTO save(ParticipantDTO dto, UploadedImage upload) {
    if (upload != null) {
      dto.setImage(imageService.saveImage(upload.data(), upload.contentType()).getId());
    }
    return save(dto);
  }

  /**
   * Updates the participant together with its photo. A newly uploaded file outranks the remove
   * flag: it is the more explicit intent, and the form should not offer both at once anyway. A
   * request that asks for neither keeps the photo that is there.
   */
  public ParticipantDTO updateByUuid(ParticipantDTO dto, UploadedImage upload, boolean removeImage) {
    if (dto.getParticipantUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    if (upload != null) {
      dto.setImage(imageService.saveImage(upload.data(), upload.contentType()).getId());
    } else if (removeImage) {
      dto.setImage(null);
    } else {
      dto.setImage(participantRepository.findById(dto.getParticipantUuid())
          .map(p -> p.getImage() != null ? p.getImage().getId() : null)
          .orElse(null));
    }
    return updateByUuid(dto);
  }

  private Image resolveImage(UUID imageId) {
    if (imageId == null) {
      return null;
    }
    return imageRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image with id " + imageId + " not found"));
  }
}

