package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.tracks.TrackPdfRequest;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import com.jaworski.serialprotocol.service.pdf.TrackPdfService;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.tracks.InvalidTrackQueryException;
import com.jaworski.serialprotocol.service.tracks.TrackLogSource;
import com.jaworski.serialprotocol.service.tracks.TrackQueryBusyException;
import com.jaworski.serialprotocol.service.tracks.TrackQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;
import org.springframework.stereotype.Controller;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The tracks page, the JSON it draws from and the PDF it prints to. Public, like {@code /chart}. */
@Controller
@RequiredArgsConstructor
public class TracksController {

    private final TrackQueryService queryService;
    private final TrackLogSource logSource;
    private final TrackPdfService pdfService;
    private final TracksProperties properties;
    private final WebSocketPublisher webSocketPublisher;
    private final JsonMapper jsonMapper;

    @GetMapping("/tracks")
    public String tracks(Model model) {
        model.addAttribute(MapController.ATTRIBUTE_NAME, "track");
        model.addAttribute("sessions", webSocketPublisher.openPageCount());
        model.addAttribute("ships", Models.values());
        model.addAttribute("displayZone", properties.displayZone().getId());
        model.addAttribute("gapSeconds", properties.gapSeconds());
        model.addAttribute("pdfBoxWidthMm", TrackPdfService.IMAGE_BOX_WIDTH_MM);
        model.addAttribute("pdfBoxHeightMm", TrackPdfService.IMAGE_BOX_HEIGHT_MM);
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

    /** The page posts a form into a new tab, so an error is plain text the tab can show as it is. */
    @PostMapping("/tracks/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) MultipartFile image,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                      @RequestParam int silhouettes,
                                      @RequestParam List<Integer> ships,
                                      @RequestParam double widthMeters,
                                      @RequestParam(required = false) String captains) throws IOException {
        try {
            var request = new TrackPdfRequest(image == null ? null : image.getBytes(), from, to, silhouettes, ships, widthMeters,
                    parseCaptains(captains));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(TrackPdfService.fileName(request)).build().toString())
                    .body(pdfService.create(request));
        } catch (InvalidTrackQueryException e) {
            return ResponseEntity.badRequest()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The names are personal data: neither they nor the parser's message, which quotes them, are logged or echoed. */
    private Map<Integer, String> parseCaptains(String json) {
        try {
            return json == null ? null : jsonMapper.readValue(json, new TypeReference<>() { });
        } catch (JacksonException e) {
            throw new InvalidTrackQueryException("The captains are not valid.");
        }
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
