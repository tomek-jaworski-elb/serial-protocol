package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.CheckBoxOption;
import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.mappers.InstructorMapper;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.InstructorService;
import com.jaworski.serialprotocol.service.db.StudentService;
import com.jaworski.serialprotocol.service.tracks.TrackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Controller
public class MapController {

    private static final Logger LOG = LoggerFactory.getLogger(MapController.class);
    public static final String ATTRIBUTE_TRACK_MAP = "trackMap";
    public static final String ATTRIBUTE_NAME = "name";
    private static final String PASS_SERVICE = "pass-service";
    private static final String ACTIVE_SESSION = "sessions";
    private final TrackService trackService;
    private final StudentService studentService;
    private final InstructorService instructorService;
    private final WebSocketPublisher webSockerService;

    @GetMapping(path = {"/", "/index.html", "/index", "/index.htm"})
    public String index(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "home");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "index";
    }

    @GetMapping("/terminal")
    public String terminal(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "terminal");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "terminal";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "about");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "about";
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = ATTRIBUTE_NAME, required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute(ATTRIBUTE_NAME, name);
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "greetings";
    }

    @GetMapping("/chart")
    public String greeting(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "chart");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "chart";
    }

    @GetMapping("/tracks")
    public String tracks(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "track");
        model.addAttribute(ATTRIBUTE_TRACK_MAP, Collections.emptyMap());
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "tracks";
    }

    @PostMapping("/tracks")
    public String submitForm(@ModelAttribute CheckBoxOption checkBoxOption, Model model) {
        LOG.info("{}", checkBoxOption);
        model.addAttribute(ATTRIBUTE_TRACK_MAP, Collections.emptyMap());
        if (!checkBoxOption.getModels().isEmpty()) {
            Map<Integer, List<LogItem>> trackMap = trackService.getModels(checkBoxOption);

            LOG.info("Result LogItems size {}", trackMap.size());
            trackMap.keySet()
                    .forEach(integer -> LOG.info("Key {} size: {}", integer, trackMap.get(integer).size()));
            model.addAttribute(ATTRIBUTE_TRACK_MAP, trackMap);
            model.addAttribute("noData", trackMap.isEmpty() ? "No data for selected models" : "");
        }
        model.addAttribute(ATTRIBUTE_NAME, "track");
        model.addAttribute("checkboxForm", checkBoxOption);
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "tracks";
    }
    @PreAuthorize("hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getRole()) or " +
            "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getName() + '_T')")
    @GetMapping("/name-service")
    public String passService(Model model,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", defaultValue = "20") int size) {
        model.addAttribute(ATTRIBUTE_NAME, PASS_SERVICE);
        return getNameModel(model, page, size);
    }

    private String getNameModel(Model model, int page, int size) {
        Page<StudentDTO> namesPage = studentService.getStudentsPaginated(page, size);
        Collection<StudentDTO> namesLatest = studentService.getLatestWeekAllStudents();
        model.addAttribute("names", namesPage.getContent());
        model.addAttribute("namesLatest", namesLatest);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", namesPage.getTotalPages());
        model.addAttribute("totalElements", namesPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute(ATTRIBUTE_NAME, "name-service");
        return "name-service";
    }

    @GetMapping("/login")
    public String login(Model model, @RequestParam(name = "error", required = false) String error) {
        model.addAttribute(ATTRIBUTE_NAME, "login");
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "login";
    }

    @PostMapping("/logout")
    public String logout(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "logout");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "redirect:/";
    }

    @PreAuthorize("hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getRole()) or " +
            "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getName() + '_T')")
    @GetMapping("/instructor-service")
    public String instructorService(Model model,
                                    @RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<InstructorDto> instructorsPage = instructorService.findAllPaginated(page, size)
                .map(InstructorMapper::mapToDto);
        model.addAttribute(ATTRIBUTE_NAME, "instructor-service");
        model.addAttribute("instructors", instructorsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", instructorsPage.getTotalPages());
        model.addAttribute("totalElements", instructorsPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        return "instructor-service";
    }
}


