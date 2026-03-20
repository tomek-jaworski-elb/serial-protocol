package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.mappers.custom.LecturerMapper;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturerService {

  private final LecturerRepository lecturerRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(LecturerService.class);

  public List<LecturerDTO> findAll() {
    return lecturerRepository.findAll().stream()
            .map(LecturerMapper::mapToDTO)
            .toList();
  }

  public LecturerDTO findById(Long id) {
    return  lecturerRepository.findById(id)
            .map(LecturerMapper::mapToDTO)
            .orElse(null);
  }

  public LecturerDTO save(LecturerDTO dto) {
    Lecturer lecturer = LecturerMapper.mapToEntity(dto);
    Lecturer savedLecturer = lecturerRepository.save(lecturer);
    return LecturerMapper.mapToDTO(savedLecturer);
  }

  public void deleteById(Long id) {
    lecturerRepository.deleteById(id);
  }

  public LecturerDTO updateById(LecturerDTO dto) {
    Lecturer lecturer = LecturerMapper.mapToEntity(dto);
    Lecturer updatedLecturer = lecturerRepository.save(lecturer);
    return LecturerMapper.mapToDTO(updatedLecturer);
  }
}
