package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.Personel;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.restclient.DiscoveryService;
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
    private final DiscoveryService discoveryService;

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
        Collection<Personel> names = null;
        try {
            names = discoveryService.getNames();
        } catch (CustomRestException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("names", names);
        model.addAttribute("name", "name-service");
        return "name-service";
    }
}


