package com.jaworski.serialprotocol.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MapController {

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
    public String greeting(@RequestParam(name="name", required=false, defaultValue="World") String name, Model model) {
        model.addAttribute("name", name);
        return "greetings";
    }

    @GetMapping("/chart")
    public String greeting(Model model) {
        model.addAttribute("name", "chart");
        return "chart";
    }
}


