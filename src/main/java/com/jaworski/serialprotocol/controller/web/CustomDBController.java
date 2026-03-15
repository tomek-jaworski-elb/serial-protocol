package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;

@RequiredArgsConstructor
@Controller
public class CustomDBController {

  private static final Logger LOG = LoggerFactory.getLogger(CustomDBController.class);
  public static final String ATTRIBUTE_NAME = "name";
  private static final String ACTIVE_SESSION = "sessions";
  private final WebSocketPublisher webSockerService;
  private final CourseTypeService courseTypeService;

  @GetMapping("/courses-service")
  public String coursesService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "courses-service");
    model.addAttribute("courses", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/courses-service";
  }

  @GetMapping("/trainer-service")
  public String trainerService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "trainer-service");
    model.addAttribute("trainers", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/trainer-service";
  }

  @GetMapping("/lecturer-service")
  public String lecturerService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "lecturer-service");
    model.addAttribute("lecturers", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/lecturer-service";
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
    model.addAttribute("participants", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/participant-service";
  }

}
