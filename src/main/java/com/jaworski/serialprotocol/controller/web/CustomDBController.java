package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CourseCounterService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
import com.jaworski.serialprotocol.service.db.custom.ThumbnailGenerator;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import jakarta.validation.ConstraintViolationException;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.beans.PropertyEditorSupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
@Controller
public class CustomDBController {

  private static final Logger LOG = LoggerFactory.getLogger(CustomDBController.class);
  public static final String ATTRIBUTE_NAME = "name";
  private static final String ACTIVE_SESSION = "sessions";
  private final WebSocketPublisher webSockerService;
  private final CourseTypeService courseTypeService;
  private final CourseCounterService courseCounterService;
  private final CoursesService coursesService;
  private final TrainerService trainerService;
  private final LecturerService lecturerService;
  private final TechnicianService technicianService;
  private final ParticipantService participantService;
  private final ImageService imageService;
  private static final int MAX_UPLOAD_IMAGES = 6;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
      "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
  );

  private static String sanitizeContentType(String rawContentType) {
    if (rawContentType == null) {
      return "application/octet-stream";
    }
    String normalized = rawContentType.trim().toLowerCase();
    // strip parameters (e.g. "image/jpeg; charset=utf-8")
    int semicolon = normalized.indexOf(';');
    String base = semicolon >= 0 ? normalized.substring(0, semicolon).trim() : normalized;
    return ALLOWED_IMAGE_TYPES.contains(base) ? base : "application/octet-stream";
  }

  /**
   * Converts empty strings submitted from HTML forms to null,
   * so optional fields (email, nickname, etc.) with Bean Validation
   * annotations (@Email, @Size) are not triggered on blank input.
   */
  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) {
        setValue(text == null || text.isBlank() ? null : text.trim());
      }
    });
  }

  @GetMapping("/courses-service")
  public String coursesService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "courses-service");
    Page<CoursesDTO> coursesPage = coursesService.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    model.addAttribute("courses", coursesPage.getContent());
    model.addAttribute("coursesPage", coursesPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute("participants", participantService.findAll());
    List<CourseTypeDTO> courseTypes = courseTypeService.findAll();
    model.addAttribute("courseTypes", courseTypes);
    Map<Long, CourseTypeDTO> courseTypeMap = courseTypes.stream()
        .collect(Collectors.toMap(CourseTypeDTO::getId, ct -> ct));
    model.addAttribute("courseTypeMap", courseTypeMap);
    model.addAttribute("trainers", trainerService.findAll());
    model.addAttribute("lecturers", lecturerService.findAll());
    model.addAttribute("technicians", technicianService.findAll());
    model.addAttribute("courseCounters", courseCounterService.findAll());
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add course. Please verify your input.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update course. Please verify your input.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete course.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to assign participant to course.");
    }
    return "redirect:/participant-service";
  }

  @GetMapping("/trainer-service")
  public String trainerService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "trainer-service");
    Page<TrainerDTO> trainerPage = trainerService.findAll(PageRequest.of(page, size));
    model.addAttribute("trainers", trainerPage.getContent());
    model.addAttribute("trainerPage", trainerPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/trainer-service";
  }

  @PostMapping("/trainer-service/add")
  public String addTrainer(@ModelAttribute TrainerDTO trainerDTO,
                           @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                           RedirectAttributes redirectAttributes) {
    try {
      trainerDTO.setId(null);
      Set<UUID> uploadedImages = uploadImages(imageFiles, MAX_UPLOAD_IMAGES);
      trainerDTO.setImagesUuid(uploadedImages);
      trainerService.save(trainerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add trainer. payload={}", trainerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add trainer. Please verify your input.");
    }
    return "redirect:/trainer-service";
  }

  @PostMapping("/trainer-service/update")
  public String updateTrainer(@ModelAttribute TrainerDTO trainerDTO,
                              @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                              @RequestParam(value = "removeImageUuids", required = false) List<UUID> removeImageUuids,
                              RedirectAttributes redirectAttributes) {
    try {
      if (trainerDTO.getId() == null) {
        throw new IllegalArgumentException("Trainer id is required for update");
      }
      TrainerDTO existingTrainer = trainerService.findById(trainerDTO.getId());
      Set<UUID> existingImages = existingTrainer != null ? existingTrainer.getImagesUuid() : null;
      trainerDTO.setImagesUuid(mergeImages(existingImages, removeImageUuids, imageFiles));
      trainerService.update(trainerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update trainer. payload={}", trainerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update trainer. Please verify your input.");
    }
    return "redirect:/trainer-service";
  }

  @PostMapping("/trainer-service/delete/{id}")
  public String deleteTrainer(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      trainerService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Trainer deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete trainer. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete trainer.");
    }
    return "redirect:/trainer-service";
  }

  @GetMapping("/lecturer-service")
  public String lecturerService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "lecturer-service");
    Page<LecturerDTO> lecturerPage = lecturerService.findAll(PageRequest.of(page, size));
    model.addAttribute("lecturers", lecturerPage.getContent());
    model.addAttribute("lecturerPage", lecturerPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/lecturer-service";
  }

  @PostMapping("/lecturer-service/add")
  public String addLecturer(@ModelAttribute LecturerDTO lecturerDTO,
                            @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                            RedirectAttributes redirectAttributes) {
    try {
      lecturerDTO.setId(null);
      Set<UUID> uploadedImages = uploadImages(imageFiles, MAX_UPLOAD_IMAGES);
      lecturerDTO.setImagesUuid(uploadedImages);
      lecturerService.save(lecturerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add lecturer. payload={}", lecturerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add lecturer. Please verify your input.");
    }
    return "redirect:/lecturer-service";
  }

  @PostMapping("/lecturer-service/update")
  public String updateLecturer(@ModelAttribute LecturerDTO lecturerDTO,
                               @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                               @RequestParam(value = "removeImageUuids", required = false) List<UUID> removeImageUuids,
                               RedirectAttributes redirectAttributes) {
    try {
      if (lecturerDTO.getId() == null) {
        throw new IllegalArgumentException("Lecturer id is required for update");
      }
      LecturerDTO existingLecturer = lecturerService.findById(lecturerDTO.getId());
      Set<UUID> existingImages = existingLecturer != null ? existingLecturer.getImagesUuid() : null;
      lecturerDTO.setImagesUuid(mergeImages(existingImages, removeImageUuids, imageFiles));
      lecturerService.updateById(lecturerDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update lecturer. payload={}", lecturerDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update lecturer. Please verify your input.");
    }
    return "redirect:/lecturer-service";
  }

  @PostMapping("/lecturer-service/delete/{id}")
  public String deleteLecturer(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      lecturerService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete lecturer. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete lecturer.");
    }
    return "redirect:/lecturer-service";
  }

  @GetMapping("/technician-service")
  public String technicianService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "technician-service");
    Page<TechnicianDTO> technicianPage = technicianService.findAll(PageRequest.of(page, size));
    model.addAttribute("technicians", technicianPage.getContent());
    model.addAttribute("technicianPage", technicianPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/technician-service";
  }

  @PostMapping("/technician-service/add")
  public String addTechnician(@ModelAttribute TechnicianDTO technicianDTO,
                              @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                              RedirectAttributes redirectAttributes) {
    try {
      technicianDTO.setId(null);
      Set<UUID> uploadedImages = uploadImages(imageFiles, MAX_UPLOAD_IMAGES);
      technicianDTO.setImagesUuid(uploadedImages);
      technicianService.save(technicianDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Technician added successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot add technician. payload={}", technicianDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add technician. Please verify your input.");
    }
    return "redirect:/technician-service";
  }

  @PostMapping("/technician-service/update")
  public String updateTechnician(@ModelAttribute TechnicianDTO technicianDTO,
                                 @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                                 @RequestParam(value = "removeImageUuids", required = false) List<UUID> removeImageUuids,
                                 RedirectAttributes redirectAttributes) {
    try {
      if (technicianDTO.getId() == null) {
        throw new IllegalArgumentException("Technician id is required for update");
      }
      TechnicianDTO existing = technicianService.findById(technicianDTO.getId());
      Set<UUID> existingImages = existing != null ? existing.getImagesUuid() : null;
      technicianDTO.setImagesUuid(mergeImages(existingImages, removeImageUuids, imageFiles));
      technicianService.updateById(technicianDTO);
      redirectAttributes.addFlashAttribute("successMessage", "Technician updated successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot update technician. payload={}", technicianDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update technician. Please verify your input.");
    }
    return "redirect:/technician-service";
  }

  @PostMapping("/technician-service/delete/{id}")
  public String deleteTechnician(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      technicianService.deleteById(id);
      redirectAttributes.addFlashAttribute("successMessage", "Technician deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete technician. id={}", id, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete technician.");
    }
    return "redirect:/technician-service";
  }

  @GetMapping("/course-type-service")
  public String courseTypeService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "course-type-service");
    Page<CourseTypeDTO> courseTypePage = courseTypeService.findAll(PageRequest.of(page, size));
    model.addAttribute("courseTypes", courseTypePage.getContent());
    model.addAttribute("courseTypePage", courseTypePage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add course type. Please verify your input.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update course type. Please verify your input.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete course type.");
    }
    return "redirect:/course-type-service";
  }

  @GetMapping("/participant-service")
  public String participantService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "participant-service");
    Page<ParticipantDTO> participantPage = participantService.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    List<ParticipantDTO> participants = participantPage.getContent();
    model.addAttribute("participants", participants);
    model.addAttribute("participantPage", participantPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute("nextId", participantService.nextId());
    model.addAttribute("courseTypes", courseTypeService.findAll());
    Map<UUID, List<CoursesDTO>> coursesByParticipant = new HashMap<>();
    participants.forEach(p -> coursesByParticipant.put(p.getParticipantUuid(), coursesService.findByParticipantUuid(p.getParticipantUuid())));
    model.addAttribute("coursesByParticipant", coursesByParticipant);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/participant-service";
  }

  @PostMapping("/participant-service/add")
  public String addParticipant(@ModelAttribute ParticipantDTO participantDTO,
                               @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                               RedirectAttributes redirectAttributes) {
    try {
      participantDTO.setParticipantUuid(null);
      UUID uploadedImage = uploadSingleImage(imageFile);
      participantDTO.setImage(uploadedImage);
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add participant. Please verify your input.");
    }
    return "redirect:/participant-service";
  }

  @PostMapping("/participant-service/update")
  public String updateParticipant(@ModelAttribute ParticipantDTO participantDTO,
                                  @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                  @RequestParam(value = "removeImage", required = false) boolean removeImage,
                                  RedirectAttributes redirectAttributes) {
    try {
      if (participantDTO.getParticipantUuid() == null) {
        throw new IllegalArgumentException("UUID is required for update");
      }
      UUID uploadedImage = uploadSingleImage(imageFile);
      if (uploadedImage != null) {
        // A newly uploaded file outranks the remove checkbox: it is the more
        // explicit intent, and the form should not offer both at once anyway.
        participantDTO.setImage(uploadedImage);
      } else if (removeImage) {
        participantDTO.setImage(null);
      } else {
        ParticipantDTO existingParticipant = participantService.findByUuid(participantDTO.getParticipantUuid());
        if (existingParticipant != null) {
          participantDTO.setImage(existingParticipant.getImage());
        }
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update participant. Please verify your input.");
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
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete participant.");
    }
    return "redirect:/participant-service";
  }

  @GetMapping("/course-counter-service")
  public String courseCounterService(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "course-counter-service");
    Page<CourseCounterDTO> counterPage = courseCounterService.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "counter")));
    List<CourseCounterDTO> counters = counterPage.getContent();
    model.addAttribute("courseCounters", counters);
    model.addAttribute("counterPage", counterPage);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    model.addAttribute("nextCounter", courseCounterService.nextCounter());
    Map<UUID, List<CoursesDTO>> coursesByCourseCounter = new HashMap<>();
    counters.forEach(cc -> coursesByCourseCounter.put(cc.uuid(), coursesService.findByCourseCounterUuid(cc.uuid())));
    model.addAttribute("coursesByCourseCounter", coursesByCourseCounter);
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/course-counter-service";
  }

  @PostMapping("/course-counter-service/add")
  public String addCourseCounter(@ModelAttribute CourseCounterDTO courseCounterDTO,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 RedirectAttributes redirectAttributes) {
    try {
      UUID uploadedImage = uploadSingleImage(imageFile);
      CourseCounterDTO toSave = new CourseCounterDTO(null, courseCounterDTO.counter(), uploadedImage);
      courseCounterService.save(toSave);
      redirectAttributes.addFlashAttribute("successMessage", "Course counter added successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot add course counter. payload={}", courseCounterDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot add course counter. payload={}", courseCounterDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to add course counter. Please verify your input.");
    }
    return "redirect:/course-counter-service";
  }

  @PostMapping("/course-counter-service/update")
  public String updateCourseCounter(@ModelAttribute CourseCounterDTO courseCounterDTO,
                                    @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                    @RequestParam(value = "removeImage", required = false) boolean removeImage,
                                    RedirectAttributes redirectAttributes) {
    try {
      if (courseCounterDTO.uuid() == null) {
        throw new IllegalArgumentException("UUID is required for update");
      }
      UUID uploadedImage = uploadSingleImage(imageFile);
      UUID imageUuid = uploadedImage;
      if (uploadedImage == null && !removeImage) {
        // Same precedence as elsewhere: an uploaded file wins, then an explicit
        // remove, and only a request that asks for neither keeps what is there.
        CourseCounterDTO existing = courseCounterService.getByUuid(courseCounterDTO.uuid())
                .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + courseCounterDTO.uuid() + " not found"));
        imageUuid = existing.imageUuid();
      }

      CourseCounterDTO toUpdate = new CourseCounterDTO(courseCounterDTO.uuid(), courseCounterDTO.counter(), imageUuid);
      courseCounterService.update(toUpdate);
      redirectAttributes.addFlashAttribute("successMessage", "Course counter updated successfully.");
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot update course counter. payload={}", courseCounterDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot update course counter. payload={}", courseCounterDTO, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update course counter. Please verify your input.");
    }
    return "redirect:/course-counter-service";
  }

  @PostMapping("/course-counter-service/delete/{uuid}")
  public String deleteCourseCounter(@PathVariable UUID uuid, RedirectAttributes redirectAttributes) {
    try {
      courseCounterService.delete(uuid);
      redirectAttributes.addFlashAttribute("successMessage", "Course counter deleted successfully.");
    } catch (RuntimeException e) {
      LOG.error("Cannot delete course counter. uuid={}", uuid, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete course counter.");
    }
    return "redirect:/course-counter-service";
  }

  private static final String THUMB_VARIANT = "thumb";
  /**
   * Revalidate every time rather than trusting a copy for N minutes.
   *
   * <p>These are photos of people and deleting one has to mean it is gone. With
   * max-age the browser keeps serving a deleted photo from disk until the age
   * expires — the server answers 404 while the cache still hands out the image.
   * "no-cache" still caches; it just asks first, and an unchanged image comes back
   * as an empty 304, so nearly all of the bandwidth saving remains.</p>
   */
  private static final String IMAGE_CACHE_CONTROL = "private, no-cache";

  /**
   * Serves a stored image, optionally downscaled via {@code ?size=thumb}.
   *
   * <p>Caching note that looks wrong until you check it: Spring Security's
   * CacheControlHeadersWriter would normally stamp {@code no-store} on every response,
   * but it skips whenever Cache-Control is already set, and skips 304s outright. The
   * header set here therefore survives.</p>
   */
  @GetMapping("/custom/image/{uuid}")
  public ResponseEntity<byte[]> imageByUuid(
      @PathVariable UUID uuid,
      @RequestParam(required = false) String size,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

    // Only one variant is offered. Accepting arbitrary values would let a single url
    // spawn unbounded resize work.
    boolean wantsThumb = THUMB_VARIANT.equals(size);
    if (size != null && !wantsThumb) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported size: " + size);
    }

    // The bytes behind a uuid never change — replacing a photo creates a new Image row —
    // so the tag needs only the uuid, the variant and the generator version.
    String etag = "\"" + uuid + "-" + (wantsThumb ? THUMB_VARIANT : "orig")
        + "-" + ThumbnailGenerator.VERSION + "\"";

    // Answered without reading the blob or generating a thumbnail — but existence is
    // still checked, and that check is not optional. The tag is derived from the uuid
    // alone, so a purely tag-based 304 would keep telling browsers "unchanged" about
    // a photo that has since been deleted, and they would go on serving it from cache
    // forever. existsById is a primary-key lookup, so the saving is preserved.
    if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
      if (!imageService.exists(uuid)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .eTag(etag)
          .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
          .build();
    }

    byte[] data;
    String contentType;
    if (wantsThumb) {
      ImageService.ImageContent thumbnail = imageService.getThumbnail(uuid);
      if (thumbnail == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      data = thumbnail.data();
      contentType = thumbnail.contentType();
    } else {
      Image image = imageService.getImageById(uuid);
      if (image == null || image.getData() == null || image.getData().length == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      data = image.getData();
      contentType = image.getContentType();
    }

    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    if (contentType != null && !contentType.isBlank()) {
      mediaType = MediaType.parseMediaType(sanitizeContentType(contentType));
    }

    return ResponseEntity.ok()
        .contentType(mediaType)
        .eTag(etag)
        .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
        // Ignored by <img>, but it stops an uploaded SVG being opened as a top-level
        // document, which is the XSS vector for image/svg+xml. Do not remove.
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
        .body(data);
  }

  private static boolean matchesEtag(String ifNoneMatch, String etag) {
    for (String candidate : ifNoneMatch.split(",")) {
      String trimmed = candidate.trim();
      if ("*".equals(trimmed) || trimmed.equals(etag) || trimmed.equals("W/" + etag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the image set an update should end up with: what survives the user's
   * removals, plus whatever was newly uploaded.
   *
   * <p>The request carries the images to <em>remove</em>, never the ones to keep.
   * That direction matters. The edit form builds its thumbnails in JavaScript, so
   * a "keep these" list would turn any rendering failure into data loss — an empty
   * editor would submit an empty keep-list and wipe every photo. With removals, a
   * broken editor simply removes nothing. It also makes a missing parameter mean
   * "leave the images alone", which is exactly how the form behaved before.</p>
   *
   * <p>Set difference already ignores ids that are not in {@code existing}, so a
   * forged uuid is a no-op: the client can only ever subtract from the entity's
   * own set, never attach someone else's photo.</p>
   */
  private Set<UUID> mergeImages(Set<UUID> existing, List<UUID> removeUuids, MultipartFile[] imageFiles) {
    Set<UUID> kept = existing == null ? new HashSet<>() : new HashSet<>(existing);
    if (removeUuids != null) {
      kept.removeAll(removeUuids);
    }

    // Clamped at 0 so a record that somehow holds more than the limit (data from
    // before the cap existed) can still have images removed — that is the only
    // way back under the limit. Adding to such a record stays blocked.
    int budget = Math.max(0, MAX_UPLOAD_IMAGES - kept.size());

    // Checked before uploadImages() runs, because that method persists each image
    // through ImageService, which commits on its own. Controllers here are not
    // transactional, so throwing after the upload would leave orphaned rows in the
    // image table that nothing ever cleans up.
    if (usableFiles(imageFiles).size() > budget) {
      throw new IllegalArgumentException(
          "Maximum " + MAX_UPLOAD_IMAGES + " images allowed (" + kept.size() + " already in use)");
    }

    Set<UUID> merged = new HashSet<>(kept);
    merged.addAll(uploadImages(imageFiles, budget));
    return merged;
  }

  private static List<MultipartFile> usableFiles(MultipartFile[] files) {
    if (files == null || files.length == 0) {
      return List.of();
    }
    return java.util.Arrays.stream(files)
        .filter(Objects::nonNull)
        .filter(f -> !f.isEmpty())
        .toList();
  }

  private Set<UUID> uploadImages(MultipartFile[] files, int maxFiles) {
    List<MultipartFile> nonEmpty = usableFiles(files);
    if (nonEmpty.isEmpty()) {
      return new HashSet<>();
    }

    if (nonEmpty.size() > maxFiles) {
      throw new IllegalArgumentException("Maximum " + maxFiles + " images allowed");
    }

    List<Image> images = nonEmpty.stream().map(file -> {
      try {
        Image image = new Image();
        image.setData(file.getBytes());
        image.setContentType(sanitizeContentType(file.getContentType()));
        return image;
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read uploaded photo", e);
      }
    }).toList();
    return imageService.saveAllImages(images);
  }

  private UUID uploadSingleImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try {
      Image image = imageService.saveImage(
          file.getBytes(),
          sanitizeContentType(file.getContentType())
      );
      return image.getId();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded photo", e);
    }
  }

}
