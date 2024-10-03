package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.authorization.AuthorizationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Controller
public class MapController {

    private static final Logger LOG = LogManager.getLogger(MapController.class);
    private final RestNameService restNameService;
    private final AuthorizationService authorizationService;
    private final TrackService trackService;

    @GetMapping(path = {"/", "/index.html", "/index", "/index.htm"})
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

    @GetMapping("/tracks")
    public String tracks(Model model) {
        model.addAttribute("name", "track");
        List<LogItem> trackServiceModel = trackService.getModel(logItem -> logItem.getModelTrack().getModelName() == 1);
        model.addAttribute("modelTrack", trackServiceModel);
        LOG.info("Model: {}", model.getAttribute("modelTrack"));
        return "tracks";
    }

    @PostMapping("/name-service")
    public String passService(Model model, @RequestParam(defaultValue = "") String password) {
        model.addAttribute("name", "pass-service");
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
        model.addAttribute("name", "name-service");
        return "name-service";
    }

    @GetMapping("/name-service")
    public String passServiceGet(Model model) {
        model.addAttribute("name", "pass-service");
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


