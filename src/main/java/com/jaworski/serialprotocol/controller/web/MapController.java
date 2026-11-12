package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.mappers.InstructorMapper;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.InstructorService;
import com.jaworski.serialprotocol.service.db.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;

@RequiredArgsConstructor
@Controller
public class MapController {

    public static final String ATTRIBUTE_NAME = "name";
    /** The two chart settings the browser needs, read once at render. */
    private static final String SHIP_SMOOTHING_MS = "shipPositionSmoothingMs";
    private static final String COG_MIN_SPEED_KN = "cogMinSpeedKn";
    private static final String PASS_SERVICE = "pass-service";
    private static final String ACTIVE_SESSION = "sessions";
    private final StudentService studentService;
    private final InstructorService instructorService;
    private final WebSocketPublisher webSockerService;
    private final Resources resources;

    @GetMapping(path = {"/", "/index.html", "/index", "/index.htm"})
    public String index(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "home");
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "index";
    }

    @GetMapping("/terminal")
    public String terminal(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "terminal");
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "terminal";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "about");
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "about";
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = ATTRIBUTE_NAME, required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute(ATTRIBUTE_NAME, name);
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "greetings";
    }

    @GetMapping("/chart")
    public String chart(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "chart");
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        model.addAttribute("isTestEnabled", resources.isTestEnabled());
        model.addAttribute(SHIP_SMOOTHING_MS, resources.getShipPositionSmoothingMs());
        model.addAttribute(COG_MIN_SPEED_KN, resources.getCogMinSpeedKn());
        model.addAttribute("shipModels", Models.values());
        return "chart";
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
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "login";
    }

    @PostMapping("/logout")
    public String logout(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "logout");
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
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
        model.addAttribute(ACTIVE_SESSION, webSockerService.openPageCount());
        return "instructor-service";
    }
}


