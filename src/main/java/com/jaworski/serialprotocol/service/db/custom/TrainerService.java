package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.mappers.custom.TrainerMapper;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainerService {

  private final TrainerRepository trainerRepository;
  private final static Logger LOGGER = LoggerFactory.getLogger(TrainerService.class);

  public List<TrainerDTO> findAll() {
    return trainerRepository.findAll().stream()
            .map(TrainerMapper::mapToDTO)
            .toList();
  }

  public TrainerDTO findById(Long id) {
    return trainerRepository.findById(id)
            .map(TrainerMapper::mapToDTO)
            .orElse(null);
  }

  public TrainerDTO save(TrainerDTO trainerDTO) {
    Trainer trainer = trainerRepository.save(TrainerMapper.mapToEntity(trainerDTO));
    return TrainerMapper.mapToDTO(trainer);
  }

  public void deleteById(Long id) {
    trainerRepository.deleteById(id);
  }

  public TrainerDTO update(TrainerDTO trainerDTO) {
    if (trainerDTO.getId() == null) {
      throw new IllegalArgumentException("Trainer id is required for update");
    }
    if (!trainerRepository.existsById(trainerDTO.getId())) {
      throw new IllegalArgumentException("Trainer with id " + trainerDTO.getId() + " not found");
    }
    return save(trainerDTO);
  }
}
