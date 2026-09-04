package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CourseCounterService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.beans.PropertyEditorSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final ImageUploadCoordinator imageUploads;
  private static final int DEFAULT_PAGE_SIZE = 10;

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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
    return "custom/trainer-service";
  }

  @PostMapping("/trainer-service/add")
  public String addTrainer(@ModelAttribute TrainerDTO trainerDTO,
                           @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                           RedirectAttributes redirectAttributes) {
    try {
      trainerDTO.setId(null);
      Set<UUID> uploadedImages = imageUploads.uploadImages(imageFiles, ImageUploadCoordinator.MAX_UPLOAD_IMAGES);
      trainerDTO.setImagesUuid(uploadedImages);
      imageUploads.persistOrDiscard(uploadedImages, () -> trainerService.save(trainerDTO));
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
      ImageUploadCoordinator.MergedImages images = imageUploads.mergeImages(existingImages, removeImageUuids, imageFiles);
      trainerDTO.setImagesUuid(images.merged());
      imageUploads.persistOrDiscard(images.uploaded(), () -> trainerService.update(trainerDTO));
      redirectAttributes.addFlashAttribute("successMessage", "Trainer updated successfully.");
    } catch (ImageUploadCoordinator.ImageLimitExceededException e) {
      // Only this type is surfaced verbatim. The generic message below would leave the user
      // guessing which field was at fault, but service-layer messages carry uuids and internal
      // phrasing and are not written for a toast.
      LOG.warn("Cannot update trainer. payload={}: {}", trainerDTO, e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
    return "custom/lecturer-service";
  }

  @PostMapping("/lecturer-service/add")
  public String addLecturer(@ModelAttribute LecturerDTO lecturerDTO,
                            @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                            RedirectAttributes redirectAttributes) {
    try {
      lecturerDTO.setId(null);
      Set<UUID> uploadedImages = imageUploads.uploadImages(imageFiles, ImageUploadCoordinator.MAX_UPLOAD_IMAGES);
      lecturerDTO.setImagesUuid(uploadedImages);
      imageUploads.persistOrDiscard(uploadedImages, () -> lecturerService.save(lecturerDTO));
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
      ImageUploadCoordinator.MergedImages images = imageUploads.mergeImages(existingImages, removeImageUuids, imageFiles);
      lecturerDTO.setImagesUuid(images.merged());
      imageUploads.persistOrDiscard(images.uploaded(), () -> lecturerService.updateById(lecturerDTO));
      redirectAttributes.addFlashAttribute("successMessage", "Lecturer updated successfully.");
    } catch (ImageUploadCoordinator.ImageLimitExceededException e) {
      // Only this type is surfaced verbatim. The generic message below would leave the user
      // guessing which field was at fault, but service-layer messages carry uuids and internal
      // phrasing and are not written for a toast.
      LOG.warn("Cannot update lecturer. payload={}: {}", lecturerDTO, e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
    return "custom/technician-service";
  }

  @PostMapping("/technician-service/add")
  public String addTechnician(@ModelAttribute TechnicianDTO technicianDTO,
                              @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
                              RedirectAttributes redirectAttributes) {
    try {
      technicianDTO.setId(null);
      Set<UUID> uploadedImages = imageUploads.uploadImages(imageFiles, ImageUploadCoordinator.MAX_UPLOAD_IMAGES);
      technicianDTO.setImagesUuid(uploadedImages);
      imageUploads.persistOrDiscard(uploadedImages, () -> technicianService.save(technicianDTO));
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
      ImageUploadCoordinator.MergedImages images = imageUploads.mergeImages(existingImages, removeImageUuids, imageFiles);
      technicianDTO.setImagesUuid(images.merged());
      imageUploads.persistOrDiscard(images.uploaded(), () -> technicianService.updateById(technicianDTO));
      redirectAttributes.addFlashAttribute("successMessage", "Technician updated successfully.");
    } catch (ImageUploadCoordinator.ImageLimitExceededException e) {
      // Only this type is surfaced verbatim. The generic message below would leave the user
      // guessing which field was at fault, but service-layer messages carry uuids and internal
      // phrasing and are not written for a toast.
      LOG.warn("Cannot update technician. payload={}: {}", technicianDTO, e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
    return "custom/participant-service";
  }

  @PostMapping("/participant-service/add")
  public String addParticipant(@ModelAttribute ParticipantDTO participantDTO,
                               @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                               RedirectAttributes redirectAttributes) {
    try {
      participantDTO.setParticipantUuid(null);
      UUID uploadedImage = imageUploads.uploadSingleImage(imageFile);
      participantDTO.setImage(uploadedImage);
      imageUploads.persistOrDiscard(ImageUploadCoordinator.justUploaded(uploadedImage), () -> participantService.save(participantDTO));
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
      UUID uploadedImage = imageUploads.uploadSingleImage(imageFile);
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
      imageUploads.persistOrDiscard(ImageUploadCoordinator.justUploaded(uploadedImage), () -> participantService.updateByUuid(participantDTO));
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
    model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
    return "custom/course-counter-service";
  }

  @PostMapping("/course-counter-service/add")
  public String addCourseCounter(@ModelAttribute CourseCounterDTO courseCounterDTO,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 RedirectAttributes redirectAttributes) {
    try {
      UUID uploadedImage = imageUploads.uploadSingleImage(imageFile);
      CourseCounterDTO toSave = new CourseCounterDTO(null, courseCounterDTO.counter(), uploadedImage);
      imageUploads.persistOrDiscard(ImageUploadCoordinator.justUploaded(uploadedImage), () -> courseCounterService.save(toSave));
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
      UUID uploadedImage = imageUploads.uploadSingleImage(imageFile);
      UUID imageUuid = uploadedImage;
      if (uploadedImage == null && !removeImage) {
        // Same precedence as elsewhere: an uploaded file wins, then an explicit
        // remove, and only a request that asks for neither keeps what is there.
        CourseCounterDTO existing = courseCounterService.getByUuid(courseCounterDTO.uuid())
                .orElseThrow(() -> new IllegalArgumentException("CourseCounter with id " + courseCounterDTO.uuid() + " not found"));
        imageUuid = existing.imageUuid();
      }

      CourseCounterDTO toUpdate = new CourseCounterDTO(courseCounterDTO.uuid(), courseCounterDTO.counter(), imageUuid);
      imageUploads.persistOrDiscard(ImageUploadCoordinator.justUploaded(uploadedImage), () -> courseCounterService.update(toUpdate));
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

}
