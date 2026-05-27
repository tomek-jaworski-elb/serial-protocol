package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.PdfReportProperties;
import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseCounterService;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import com.jaworski.serialprotocol.service.pdf.PdfReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * MVC controller that generates and streams PDF reports for all domain entities.
 *
 * <h2>Endpoint pattern</h2>
 * <pre>POST /pdf/{entity}</pre>
 * Accepts {@code application/x-www-form-urlencoded} with a single {@code ids}
 * parameter containing a comma-separated list of record identifiers
 * (UUIDs or Longs depending on entity).
 *
 * <h2>Response</h2>
 * Returns {@code application/pdf} with
 * {@code Content-Disposition: inline; filename="{entity}-report-YYYYMMDD.pdf"}.
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>Malformed UUID / Long → 400 Bad Request</li>
 *   <li>IDs count exceeds {@code pdf.report.max-records} → 400 Bad Request</li>
 *   <li>PDF generation failure (IOException) → 500 Internal Server Error</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/pdf")
public class PdfReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfReportController.class);
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TrainerService trainerService;
    private final LecturerService lecturerService;
    private final TechnicianService technicianService;
    private final ParticipantService participantService;
    private final CoursesService coursesService;
    private final CourseTypeService courseTypeService;
    private final CourseCounterService courseCounterService;
    private final PdfReportService pdfReportService;
    private final PdfReportProperties pdfReportProperties;

    // ── trainers ────────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given trainer UUIDs.
     *
     * @param ids comma-separated trainer UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/trainer",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> trainerPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<TrainerDTO> dtos= idList.stream()
                .map(trainerService::findById)
                .filter(Objects::nonNull)
                .toList();

        try {
            byte[] pdf = pdfReportService.generateTrainersPdf(dtos);
            return pdfResponse(pdf, "trainers-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate trainers PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── lecturers ───────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given lecturer UUIDs.
     *
     * @param ids comma-separated lecturer UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/lecturer",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> lecturerPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<LecturerDTO> dtos= idList.stream()
                .map(lecturerService::findById)
                .filter(Objects::nonNull)
                .toList();

        try {
            byte[] pdf = pdfReportService.generateLecturersPdf(dtos);
            return pdfResponse(pdf, "lecturers-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate lecturers PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── technicians ─────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given technician UUIDs.
     *
     * @param ids comma-separated technician UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/technician",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> technicianPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<TechnicianDTO> dtos= idList.stream()
                .map(technicianService::findById)
                .filter(Objects::nonNull)
                .toList();

        try {
            byte[] pdf = pdfReportService.generateTechniciansPdf(dtos);
            return pdfResponse(pdf, "technicians-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate technicians PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── participants ────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given participant UUIDs.
     *
     * @param ids comma-separated participant UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/participant",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> participantPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<ParticipantDTO> dtos= idList.stream()
                .map(participantService::findByUuid)
                .filter(Objects::nonNull)
                .toList();

        try {
            byte[] pdf = pdfReportService.generateParticipantsPdf(dtos);
            return pdfResponse(pdf, "participants-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate participants PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── courses ─────────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given course UUIDs.
     *
     * @param ids comma-separated course UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/courses",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> coursesPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<CoursesDTO> dtos= idList.stream()
                .map(coursesService::findByUuid)
                .filter(Objects::nonNull)
                .toList();

        try {
            byte[] pdf = pdfReportService.generateCoursesPdf(dtos);
            return pdfResponse(pdf, "courses-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate courses PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── course types ────────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given course-type IDs (Long).
     *
     * @param ids comma-separated course-type Long IDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/course-type",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> courseTypePdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<Long> idList;
        try {
            idList = parseLongs(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid numeric ID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        List<CourseTypeDTO> dtos= new ArrayList<>();
        for (Long id : idList) {
            try {
                dtos.add(courseTypeService.findById(id));
            } catch (Exception e) {
                LOGGER.debug("CourseType id={} not found, skipping", id);
            }
        }

        try {
            byte[] pdf = pdfReportService.generateCourseTypesPdf(dtos);
            return pdfResponse(pdf, "course-types-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate course-types PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── course counters ─────────────────────────────────────────────────────

    /**
     * Generates a PDF report for the given course-counter UUIDs.
     *
     * <p>Because {@link CourseCounterService} does not expose a {@code findByUuid}
     * method, all counters are fetched via {@code findAll()} and then filtered
     * to the requested UUIDs.
     *
     * @param ids comma-separated course-counter UUIDs (empty → empty report)
     * @return PDF response or 400/500 on error
     */
    @PostMapping(value = "/course-counter",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> courseCounterPdf(
            @RequestParam(required = false, defaultValue = "") String ids) {

        List<UUID> idList;
        try {
            idList = parseUuids(ids);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid UUID in 'ids': " + e.getMessage());
        }
        if (exceedsMax(idList)) {
            return tooManyRecords(idList.size(), pdfReportProperties.getMaxRecords());
        }

        Set<UUID> idSet = Set.copyOf(idList);
        List<CourseCounterDTO> dtos = courseCounterService.findAllByUuids(idSet);

        try {
            byte[] pdf = pdfReportService.generateCourseCountersPdf(dtos);
            return pdfResponse(pdf, "course-counters-report");
        } catch (IOException e) {
            LOGGER.error("Failed to generate course-counters PDF", e);
            return serverError("PDF generation failed");
        }
    }

    // ── private helpers ─────────────────────────────────────────────────────

    /**
     * Parses a comma-separated string of UUIDs.
     *
     * @param raw raw request parameter value
     * @return list of parsed UUIDs (empty list when {@code raw} is blank)
     * @throws IllegalArgumentException if any token is not a valid UUID
     */
    private static List<UUID> parseUuids(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) continue;
            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + trimmed + "' is not a valid UUID", e);
            }
        }
        return result;
    }

    /**
     * Parses a comma-separated string of {@code Long} IDs.
     *
     * @param raw raw request parameter value
     * @return list of parsed Longs (empty list when {@code raw} is blank)
     * @throws IllegalArgumentException if any token is not a valid long
     */
    private static List<Long> parseLongs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) continue;
            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + trimmed + "' is not a valid numeric ID", e);
            }
        }
        return result;
    }

    /** Returns {@code true} when the list exceeds the configured max-records limit. */
    private boolean exceedsMax(List<?> list) {
        return list.size() > pdfReportProperties.getMaxRecords();
    }

    /**
     * Builds a successful {@code application/pdf} response with an
     * {@code inline} Content-Disposition header.
     *
     * @param pdfBytes  generated PDF bytes
     * @param baseName  filename prefix (date and {@code .pdf} are appended automatically)
     */
    private static ResponseEntity<byte[]> pdfResponse(byte[] pdfBytes, String baseName) {
        String filename = baseName + "-" + LocalDate.now().format(FILE_DATE_FMT) + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.inline().filename(filename).build());
        headers.setContentLength(pdfBytes.length);
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<T> badRequest(String message) {
        return (ResponseEntity<T>) ResponseEntity
                .badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
    }

    private ResponseEntity<byte[]> tooManyRecords(int actual, int max) {
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(("Too many IDs requested: %d. Maximum allowed per request is %d." +
                       " Use pagination or reduce the selection.")
                        .formatted(actual, max).getBytes());
    }

    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<T> serverError(String message) {
        return (ResponseEntity<T>) ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
    }
}
