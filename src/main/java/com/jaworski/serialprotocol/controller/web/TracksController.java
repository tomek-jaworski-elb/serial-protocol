package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.tracks.InvalidTrackQueryException;
import com.jaworski.serialprotocol.service.tracks.TrackLogSource;
import com.jaworski.serialprotocol.service.tracks.TrackQueryBusyException;
import com.jaworski.serialprotocol.service.tracks.TrackQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/** The tracks page and the JSON it draws from. Public, like {@code /chart}. */
@Controller
@RequiredArgsConstructor
public class TracksController {

    private final TrackQueryService queryService;
    private final TrackLogSource logSource;
    private final TracksProperties properties;
    private final WebSocketPublisher webSocketPublisher;

    @GetMapping("/tracks")
    public String tracks(Model model) {
        model.addAttribute(MapController.ATTRIBUTE_NAME, "track");
        model.addAttribute("sessions", webSocketPublisher.openPageCount());
        model.addAttribute("ships", Models.values());
        model.addAttribute("displayZone", properties.displayZone().getId());
        model.addAttribute("gapSeconds", properties.gapSeconds());
        // The default range is built from the server's clock in the display zone, never the browser's.
        model.addAttribute("now", LocalDateTime.now(properties.displayZone()).truncatedTo(ChronoUnit.SECONDS));
        return "tracks";
    }

    /** Days with a log, in the display zone. */
    @GetMapping("/tracks/api/days")
    @ResponseBody
    public Map<String, Object> days() {
        return Map.of("days", logSource.availableDays().stream().map(LocalDate::toString).toList(),
                "displayZone", properties.displayZone().getId());
    }

    /** Points of the ships {@code models} between {@code from} and {@code to}, display zone. */
    @GetMapping("/tracks/api/points")
    @ResponseBody
    public TrackQueryResult points(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                   @RequestParam Set<Integer> models) {
        return queryService.query(from, to, models);
    }

    @ExceptionHandler({InvalidTrackQueryException.class, TypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String message = e instanceof InvalidTrackQueryException ? e.getMessage() : "Invalid or missing request parameter.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(TrackQueryBusyException.class)
    public ResponseEntity<Map<String, String>> busy(TrackQueryBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }
}
