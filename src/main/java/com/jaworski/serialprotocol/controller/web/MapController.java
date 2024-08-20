package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.Student;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.restclient.RestNameService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;

@RequiredArgsConstructor
@Controller
public class MapController {

    private static final Logger LOG = LogManager.getLogger(MapController.class);
    private final RestNameService restNameService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("name", "home");
        return "index";
    }

    @GetMapping("/terminal")
    public String terminal(Model model) {
        model.addAttribute("name", "terminal");
        return "terminal";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("name", "about");
        return "about";
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greetings";
    }

    @GetMapping("/chart")
    public String greeting(Model model) {
        model.addAttribute("name", "chart");
        return "chart";
    }

    @GetMapping("/name-service")
    public String nameService(Model model) {
        Collection<Student> names = null;
        try {
            names = restNameService.getNames();
        } catch (CustomRestException e) {
            LOG.error("Failed to get names from name-service", e);
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("names", names);
        model.addAttribute("name", "name-service");
        return "name-service";
    }
}


