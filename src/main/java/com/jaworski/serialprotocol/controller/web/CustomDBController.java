package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;

@RequiredArgsConstructor
@Controller
public class CustomDBController {

  private static final Logger LOG = LoggerFactory.getLogger(CustomDBController.class);
  public static final String ATTRIBUTE_NAME = "name";
  private static final String ACTIVE_SESSION = "sessions";
  private final WebSocketPublisher webSockerService;

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
    model.addAttribute("courseTypes", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/course-type-service";
  }

  @GetMapping("/participant-service")
  public String participantService(Model model) {
    model.addAttribute(ATTRIBUTE_NAME, "participant-service");
    model.addAttribute("participants", Collections.emptyList());
    model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
    return "custom/participant-service";
  }

}
