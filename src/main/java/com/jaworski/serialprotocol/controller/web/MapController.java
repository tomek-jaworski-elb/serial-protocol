package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.authorization.AuthorizationService;
import com.jaworski.serialprotocol.dto.CheckBoxOption;
import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.Student;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.restclient.RestNameService;
import com.jaworski.serialprotocol.service.tracks.TrackService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.io.Serializable;
import java.util.*;

@RequiredArgsConstructor
@Controller
public class MapController {

    private static final Logger LOG = LogManager.getLogger(MapController.class);
    public static final String ATTRIBUTE_TRACK_MAP = "trackMap";
    public static final String ATTRIBUTE_NAME = "name";
    private final RestNameService restNameService;
    private final AuthorizationService authorizationService;
    private final TrackService trackService;

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
        LOG.info("Model: {}", model.getAttribute(ATTRIBUTE_TRACK_MAP));
        return "tracks";
    }

    @PostMapping("/tracks")
    public String submitForm(@ModelAttribute CheckBoxOption checkBoxOption, Model model) {
        LOG.info("{}", checkBoxOption);
        Map<Integer, List<LogItem>> trackMap = new HashMap<>();
        checkBoxOption.getModels().forEach(modelId -> {
            List<LogItem> trackServiceModel = trackService.getModel(logItem -> logItem.getModelTrack().getModelName() == modelId);
            trackMap.put(modelId, trackServiceModel);
        });
        LOG.info("Result LogItems size {}", trackMap.size());
        model.addAttribute(ATTRIBUTE_NAME, "track");
        model.addAttribute("checkboxForm", checkBoxOption);
        model.addAttribute(ATTRIBUTE_TRACK_MAP, trackMap);
        LOG.info("Model: {}", model.getAttribute(ATTRIBUTE_TRACK_MAP));
        return "tracks";
    }

    @PostMapping("/name-service")
    public String passService(Model model, @RequestParam(defaultValue = "") String password) {
        model.addAttribute(ATTRIBUTE_NAME, "pass-service");
        return authorizationService.authorize(password) ?
                getNameModel(model) :
                getErrorString(model, password);
    }

    private String getErrorString(Model model, String password) {
        LOG.info("Wrong password: {}", password);
        model.addAttribute("error", "Wrong password");
        return "pass-service";
    }

    private String getNameModel(Model model) {
        Collection<Student> names = null;
        Collection<Student> namesLatest = null;
        try {
            names = restNameService.getNames();
            namesLatest = restNameService.getNamesLatest();
        } catch (CustomRestException e) {
            if (e.getCause() instanceof HttpStatusCodeException) {
                HttpStatusCode statusCode = ((HttpStatusCodeException) e.getCause()).getStatusCode();
                LOG.error("Http status {}", statusCode, e.getCause());
                model.addAttribute("error", "Http Status code: " + statusCode);
            } else if (e.getCause() instanceof ResourceAccessException) {
                LOG.error("Failed to get names from name-service: {}", e.getMessage());
                model.addAttribute("error", "Could not connect to name-service. " + e.getCause().getMessage());
            } else {
                LOG.error("Failed to get names from name-service", e);
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("names", names);
        model.addAttribute("namesLatest", namesLatest);
        model.addAttribute(ATTRIBUTE_NAME, "name-service");
        return "name-service";
    }

    @GetMapping("/name-service")
    public String passServiceGet(Model model) {
        model.addAttribute(ATTRIBUTE_NAME, "pass-service");
        return "pass-service";
    }

    static class Point implements Serializable {
        private final float x;
        private final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "{" +
                    "x: " + x +
                    ", y: " + y +
                    '}';
        }
    }
}


