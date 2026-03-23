package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import jakarta.validation.ConstraintViolationException;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
public class CustomDBController {

  private static final Logger LOG = LoggerFactory.getLogger(CustomDBController.class);
  public static final String ATTRIBUTE_NAME = "name";
  private static final String ACTIVE_SESSION = "sessions";
  private final WebSocketPublisher webSockerService;
  private final CourseTypeService courseTypeService;
  private final CoursesService coursesService;
  private final TrainerService trainerService;
  private final LecturerService lecturerService;
  private final ParticipantService participantService;

  @GetMapping("/courses-service")
  public String coursesService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "courses-service");
    model.addAttribute("courses", coursesService.findAll());
    model.addAttribute("participants", participantService.findAll());
    List<CourseTypeDTO> courseTypes = courseTypeService.findAll();
    model.addAttribute("courseTypes", courseTypes);
    Map<Long, CourseTypeDTO> courseTypeMap = courseTypes.stream()
        .collect(Collectors.toMap(CourseTypeDTO::getId, ct -> ct));
    model.addAttribute("courseTypeMap", courseTypeMap);
    model.addAttribute("trainers", trainerService.findAll());
    model.addAttribute("lecturers", lecturerService.findAll());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/courses-service";
  }

  @PostMapping("/courses-service/add")
  public String addCourse(@ModelAttribute CoursesDTO coursesDTO, RedirectAttributes redirectAttributes) {
    try {
      coursesDTO.setUuid(null);
      coursesService.save(coursesDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Course added successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot add course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot add course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add course: " + e.getMessage());
    }
    return "redirect:/courses-service";
  }

  @PostMapping("/courses-service/update")
  public String updateCourse(@ModelAttribute CoursesDTO coursesDTO, RedirectAttributes redirectAttributes) {
    try {
      if (coursesDTO.getUuid() == null) {
        throw new IllegalArgumentException("UUID is required for update");
      }
      coursesService.update(coursesDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Course updated successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot update course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot update course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update course: " + e.getMessage());
    }
    return "redirect:/courses-service";
  }

  @PostMapping("/courses-service/delete/{uuid}")
  public String deleteCourse(@PathVariable UUID uuid, RedirectAttributes redirectAttributes) {
    try {
      coursesService.deleteByUuid(uuid);
      redirectAttributes.addFlashAttribute("successMessage", "Course deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete course. uuid={}", uuid, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete course: " + e.getMessage());
    }
    return "redirect:/courses-service";
  }

  @PostMapping("/courses-service/add-participant")
  public String addParticipantToCourse(@ModelAttribute CoursesDTO coursesDTO, RedirectAttributes redirectAttributes) {
    try {
      coursesDTO.setUuid(null);
      coursesService.save(coursesDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Participant assigned to course successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot assign participant to course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot assign participant to course. payload={}", coursesDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to assign participant to course: " + e.getMessage());
    }
    return "redirect:/participant-service";
  }

  @GetMapping("/trainer-service")
  public String trainerService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "trainer-service");
    model.addAttribute("trainers", trainerService.findAll());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/trainer-service";
  }

  @PostMapping("/trainer-service/add")
  public String addTrainer(@ModelAttribute TrainerDTO trainerDTO,
                           @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                           RedirectAttributes redirectAttributes) {
    try {
      trainerDTO.setId(null);
      trainerDTO.setPhoto(extractPhotoBytes(photoFile));
      trainerService.save(trainerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add trainer. payload={}", trainerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add trainer: " + e.getMessage());
    }
    return "redirect:/trainer-service";
  }

  @PostMapping("/trainer-service/update")
  public String updateTrainer(@ModelAttribute TrainerDTO trainerDTO,
                              @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                              RedirectAttributes redirectAttributes) {
    try {
      if (trainerDTO.getId() == null) {
        throw new IllegalArgumentException("Trainer id is required for update");
      }

      byte[] uploadedPhoto = extractPhotoBytes(photoFile);
      if (uploadedPhoto == null || uploadedPhoto.length == 0) {
        TrainerDTO currentTrainer = trainerService.findById(trainerDTO.getId());
        if (currentTrainer != null) {
          trainerDTO.setPhoto(currentTrainer.getPhoto());
        }
      } else {
        trainerDTO.setPhoto(uploadedPhoto);
      }

      trainerService.update(trainerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update trainer. payload={}", trainerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update trainer: " + e.getMessage());
    }
    return "redirect:/trainer-service";
  }

  @PostMapping("/trainer-service/delete/{id}")
  public String deleteTrainer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      trainerService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete trainer. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete trainer: " + e.getMessage());
    }
    return "redirect:/trainer-service";
  }

  @GetMapping("/lecturer-service")
  public String lecturerService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "lecturer-service");
    model.addAttribute("lecturers", lecturerService.findAll());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/lecturer-service";
  }

  @PostMapping("/lecturer-service/add")
  public String addLecturer(@ModelAttribute LecturerDTO lecturerDTO,
                            @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                            RedirectAttributes redirectAttributes) {
    try {
      lecturerDTO.setLecturerId(null);
      lecturerDTO.setPhoto(extractPhotoBytes(photoFile));
      lecturerService.save(lecturerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add lecturer. payload={}", lecturerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add lecturer: " + e.getMessage());
    }
    return "redirect:/lecturer-service";
  }

  @PostMapping("/lecturer-service/update")
  public String updateLecturer(@ModelAttribute LecturerDTO lecturerDTO,
                               @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                               RedirectAttributes redirectAttributes) {
    try {
      if (lecturerDTO.getLecturerId() == null) {
        throw new IllegalArgumentException("Lecturer id is required for update");
      }

      byte[] uploadedPhoto = extractPhotoBytes(photoFile);
      if (uploadedPhoto == null || uploadedPhoto.length == 0) {
        LecturerDTO current = lecturerService.findById(lecturerDTO.getLecturerId());
        if (current != null) {
          lecturerDTO.setPhoto(current.getPhoto());
        }
      } else {
        lecturerDTO.setPhoto(uploadedPhoto);
      }

      lecturerService.updateById(lecturerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update lecturer. payload={}", lecturerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update lecturer: " + e.getMessage());
    }
    return "redirect:/lecturer-service";
  }

  @PostMapping("/lecturer-service/delete/{id}")
  public String deleteLecturer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      lecturerService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete lecturer. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete lecturer: " + e.getMessage());
    }
    return "redirect:/lecturer-service";
  }

  @GetMapping("/course-type-service")
  public String courseTypeService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "course-type-service");
    model.addAttribute("courseTypes", courseTypeService.findAll());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/course-type-service";
  }

  @PostMapping("/course-type-service/add")
  public String addCourseType(@ModelAttribute CourseTypeDTO courseTypeDTO, RedirectAttributes redirectAttributes) {
    try {
      courseTypeDTO.setId(null);
      courseTypeService.save(courseTypeDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Course type added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add course type. payload={}", courseTypeDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add course type: " + e.getMessage());
    }
    return "redirect:/course-type-service";
  }

  @PostMapping("/course-type-service/update")
  public String updateCourseType(@ModelAttribute CourseTypeDTO courseTypeDTO, RedirectAttributes redirectAttributes) {
    try {
      if (courseTypeDTO.getId() == null) {
        throw new IllegalArgumentException("Course type id is required for update");
      }

      courseTypeService.update(courseTypeDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Course type updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update course type. payload={}", courseTypeDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update course type: " + e.getMessage());
    }
    return "redirect:/course-type-service";
  }

  @PostMapping("/course-type-service/delete/{id}")
  public String deleteCourseType(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      courseTypeService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Course type deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete course type. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete course type: " + e.getMessage());
    }
    return "redirect:/course-type-service";
  }

  @GetMapping("/participant-service")
  public String participantService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "participant-service");
    List<ParticipantDTO> participants = participantService.findAll();
    model.addAttribute("participants", participants);
    model.addAttribute("nextId", participantService.nextId());
    model.addAttribute("courseTypes", courseTypeService.findAll());
    Map<UUID, List<CoursesDTO>> coursesByParticipant = new HashMap<>();
    participants.forEach(p -> coursesByParticipant.put(p.getUuid(), coursesService.findByParticipantUuid(p.getUuid())));
    model.addAttribute("coursesByParticipant", coursesByParticipant);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/participant-service";
  }

  @PostMapping("/participant-service/add")
  public String addParticipant(@ModelAttribute ParticipantDTO participantDTO,
                               @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                               RedirectAttributes redirectAttributes) {
    try {
      participantDTO.setUuid(null);
      participantDTO.setPhoto(extractPhotoBytes(photoFile));
      participantService.save(participantDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Participant added successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot add participant. payload={}", participantDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (ConstraintViolationException e) {
      String violations = e.getConstraintViolations().stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .reduce((a, b) -> a + "; " + b)
              .orElse(e.getMessage());
      LOG.warn("Validation error adding participant. payload={}: {}", participantDTO, violations);
      redirectAttributes.addFlashAttribute("errorMessage", "Validation error: " + violations);
    } catch (RuntimeException e) {
      LOG.error("Cannot add participant. payload={}", participantDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add participant: " + e.getMessage());
    }
    return "redirect:/participant-service";
  }

  @PostMapping("/participant-service/update")
  public String updateParticipant(@ModelAttribute ParticipantDTO participantDTO,
                                  @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                  RedirectAttributes redirectAttributes) {
    try {
      if (participantDTO.getUuid() == null) {
        throw new IllegalArgumentException("UUID is required for update");
      }
      byte[] uploadedPhoto = extractPhotoBytes(photoFile);
      if (uploadedPhoto == null || uploadedPhoto.length == 0) {
        ParticipantDTO current = participantService.findByUuid(participantDTO.getUuid());
        if (current != null) {
          participantDTO.setPhoto(current.getPhoto());
        }
      } else {
        participantDTO.setPhoto(uploadedPhoto);
      }
      participantService.updateByUuid(participantDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Participant updated successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot update participant. payload={}", participantDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (ConstraintViolationException e) {
      String violations = e.getConstraintViolations().stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .reduce((a, b) -> a + "; " + b)
              .orElse(e.getMessage());
      LOG.warn("Validation error updating participant. payload={}: {}", participantDTO, violations);
      redirectAttributes.addFlashAttribute("errorMessage", "Validation error: " + violations);
    } catch (RuntimeException e) {
      LOG.error("Cannot update participant. payload={}", participantDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update participant: " + e.getMessage());
    }
    return "redirect:/participant-service";
  }

  @PostMapping("/participant-service/delete/{uuid}")
  public String deleteParticipant(@PathVariable UUID uuid, RedirectAttributes redirectAttributes) {
    try {
      participantService.deleteByUuid(uuid);
      redirectAttributes.addFlashAttribute("successMessage", "Participant deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete participant. uuid={}", uuid, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete participant: " + e.getMessage());
    }
    return "redirect:/participant-service";
  }

  private byte[] extractPhotoBytes(MultipartFile photoFile) {
    if (photoFile == null || photoFile.isEmpty()) {
      return null;
    }
    try {
      return photoFile.getBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read uploaded photo", e);
    }
  }

}
