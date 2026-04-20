/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.DatabaseBackupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for database backup and restore utilities.
 * All endpoints require ADMIN role.
 */
@Controller
@RequestMapping("/db-utils")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DbUtilsController {

    private static final Logger LOG = LoggerFactory.getLogger(DbUtilsController.class);
    private static final String ACTIVE_SESSION = "sessions";
    private static final DateTimeFormatter FILENAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final DatabaseBackupService databaseBackupService;
    private final WebSocketPublisher webSockerService;

    /**
     * Shows the DB Utils page.
     */
    @GetMapping
    public String dbUtils(Model model) {
        model.addAttribute("name", "db-utils");
        model.addAttribute(ACTIVE_SESSION, webSockerService.sessionsCount());
        model.addAttribute("schemaVersion", DatabaseBackupService.SCHEMA_VERSION);
        return "db-utils";
    }

    /**
     * Creates a full database backup and streams it directly to the client as a
     * GZIP-compressed JSON file. Writes synchronously to the response output stream –
     * no intermediate byte[] buffer, handles databases of arbitrary size.
     */
    @PostMapping("/backup")
    public void backup(HttpServletResponse response) throws IOException {
        String filename = "db-backup-" + LocalDateTime.now().format(FILENAME_FORMATTER) + ".json.gz";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        databaseBackupService.createBackup(response.getOutputStream());
        response.flushBuffer();
        LOG.info("Backup download streamed: filename={}", filename);
    }

    /**
     * Accepts a previously created backup file and restores the database.
     * The uploaded file is read as a stream – no full byte[] copy in memory.
     * All existing data is replaced.
     */
    @PostMapping("/restore")
    public String restore(
            @RequestParam("backupFile") MultipartFile backupFile,
            RedirectAttributes redirectAttributes) {

        if (backupFile == null || backupFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No backup file selected.");
            return "redirect:/db-utils";
        }

        try (java.io.InputStream is = backupFile.getInputStream()) {
            databaseBackupService.restoreFromBackup(is);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Database restored successfully from '" + backupFile.getOriginalFilename() + "'.");
            LOG.info("Database restored from file: {}", backupFile.getOriginalFilename());

        } catch (IllegalArgumentException e) {
            LOG.error("Restore failed – invalid backup file: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Restore failed: " + e.getMessage());
        } catch (IOException e) {
            LOG.error("Restore failed – I/O error", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Restore failed (I/O error): " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Restore failed – unexpected error", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Restore failed: " + e.getMessage());
        }

        return "redirect:/db-utils";
    }
}
