package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.mappers.custom.ParticipantMapper;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParticipantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantService.class);
  private final ParticipantRepository participantRepository;

  public List<ParticipantDTO> findAll() {
    return participantRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
            .map(ParticipantMapper::mapToDTO)
            .toList();
  }

  public ParticipantDTO findByUuid(UUID uuid) {
    return participantRepository.findById(uuid)
            .map(ParticipantMapper::mapToDTO)
            .orElse(null);
  }

  public Long nextId() {
    return participantRepository.findMaxParticipantId() + 1;
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
    if (isIdTaken(dto.getId())) {
      throw new IllegalArgumentException("Participant with id " + dto.getId() + " already exists");
    }
    Participant participant = ParticipantMapper.mapToEntity(dto);
    Participant saved = participantRepository.save(participant);
    return ParticipantMapper.mapToDTO(saved);
  }

  public void deleteByUuid(UUID uuid) {
    participantRepository.deleteById(uuid);
  }

  public ParticipantDTO updateByUuid(ParticipantDTO dto) {
    if (dto.getUuid() == null) {
      throw new IllegalArgumentException("UUID is required for update");
    }
    if (dto.getId() != null && isIdTakenByOther(dto.getId(), dto.getUuid())) {
      throw new IllegalArgumentException("Participant id " + dto.getId() + " is already used by another participant");
    }
    Participant participant = ParticipantMapper.mapToEntity(dto);
    Participant updated = participantRepository.save(participant);
    return ParticipantMapper.mapToDTO(updated);
  }
}

