package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.authorization.AuthorizationService;
import com.jaworski.serialprotocol.dto.CheckBoxOption;
import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.service.db.StudentService;
import com.jaworski.serialprotocol.service.tracks.TrackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final AuthorizationService authorizationService;
    private final TrackService trackService;
    private final StudentService studentService;

    @GetMapping(path = {"/", "/index.html", "/index", "/index.htm"})
    public String index(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "home");
        return "index";
    }

    @GetMapping("/terminal")
    public String terminal(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "terminal");
        return "terminal";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "about");
        return "about";
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = ATTRIBUTE_NAME, required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute(ATTRIBUTE_NAME, name);
        return "greetings";
    }

    @GetMapping("/chart")
    public String greeting(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "chart");
        return "chart";
    }

    @GetMapping("/tracks")
    public String tracks(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "track");
        model.addAttribute(ATTRIBUTE_TRACK_MAP, Collections.emptyMap());
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
        return "tracks";
    }
    @PreAuthorize(value = "hasRole(hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getRole()) or" +
            " hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getName() + '_T')")
    @GetMapping("/name-service")
    public String passService(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, PASS_SERVICE);
        return getNameModel(model);
    }

    private String getNameModel(Model model) {
        Collection<StudentDTO> names = studentService.getStudents();
        Collection<StudentDTO> namesLatest = studentService.getLatestWeekAllStudents();
        model.addAttribute("names", names);
        model.addAttribute("namesLatest", namesLatest);
        model.addAttribute(ATTRIBUTE_NAME, "name-service");
        return "name-service";
    }

    @GetMapping("/login")
    public String login(Model model, @RequestParam(name = "error", required = false) String error) {
        model.addAttribute(ATTRIBUTE_NAME, "login");
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        return "login";
    }

    @GetMapping("/logout")
    public String logout(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "logout");
        return "redirect:/";
    }
}


