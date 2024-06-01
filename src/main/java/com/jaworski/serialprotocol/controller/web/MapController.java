package com.jaworski.serialprotocol.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MapController {

    @GetMapping("/")
    public String index() {
        return "maps";
    }

    @GetMapping("/terminal")
    public String terminal() {
        return "terminal";
    }
    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name="name", required=false, defaultValue="World") String name, Model model) {
        model.addAttribute("name", name);
        return "greetings";
    }
}


