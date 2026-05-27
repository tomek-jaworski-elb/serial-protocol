/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.controller.web;

import tools.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.backup.DatabaseBackupDTO;
import com.jaworski.serialprotocol.service.db.DatabaseBackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link DbUtilsController}.
 * Verifies access control, backup download, and restore HTTP behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DbUtilsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // GET /db-utils – access control
    // =========================================================================

    @Test
    void dbUtils_get_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/db-utils"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dbUtils_get_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/db-utils")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("user", "user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dbUtils_get_shouldReturnHtmlPage_whenAdmin() throws Exception {
        mockMvc.perform(get("/db-utils")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("DB Utils")));
    }

    @Test
    void dbUtils_get_shouldShowSchemaVersion_whenAdmin() throws Exception {
        mockMvc.perform(get("/db-utils")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(DatabaseBackupService.SCHEMA_VERSION)));
    }

    // =========================================================================
    // POST /db-utils/backup – access control & response shape
    // =========================================================================

    @Test
    void backup_post_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/db-utils/backup").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void backup_post_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("user", "user"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void backup_post_shouldReturn200_whenAdmin() throws Exception {
        mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void backup_post_shouldReturnOctetStream_whenAdmin() throws Exception {
        mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void backup_post_shouldSetContentDispositionAttachment_whenAdmin() throws Exception {
        mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("db-backup-")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString(".json.gz")));
    }

    @Test
    void backup_post_shouldReturnValidGzip_whenAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertTrue(body.length > 0);

        assertDoesNotThrow(() -> {
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                byte[] content = gzis.readAllBytes();
                assertTrue(content.length > 0);
            }
        }, "Response body should be valid GZIP");
    }

    @Test
    void backup_post_shouldReturnJsonWithCorrectSchemaVersion_whenAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        DatabaseBackupDTO dto;
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(body))) {
            dto = objectMapper.readValue(gzis, DatabaseBackupDTO.class);
        }

        assertNotNull(dto);
        assertEquals(DatabaseBackupService.SCHEMA_VERSION, dto.getSchemaVersion());
        assertNotNull(dto.getTimestamp());
    }

    // =========================================================================
    // POST /db-utils/restore – access control
    // =========================================================================

    @Test
    void restore_post_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(multipart("/db-utils/restore").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restore_post_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(multipart("/db-utils/restore")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("user", "user"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // POST /db-utils/restore – happy path
    // =========================================================================

    @Test
    void restore_post_shouldRedirectWithSuccessMessage_whenValidBackup() throws Exception {
        // obtain a valid backup first
        MvcResult backupResult = mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] backupBytes = backupResult.getResponse().getContentAsByteArray();

        // restore from it
        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "db-backup.json.gz",
                                "application/octet-stream", backupBytes))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/db-utils"))
                .andExpect(flash().attribute("successMessage",
                        containsString("restored successfully")));
    }

    @Test
    void restore_post_shouldRedirectWithSuccessMessage_containingFilename() throws Exception {
        byte[] backupBytes = getValidBackupBytes();

        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "my-custom-backup.json.gz",
                                "application/octet-stream", backupBytes))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("my-custom-backup.json.gz")));
    }

    // =========================================================================
    // POST /db-utils/restore – error cases
    // =========================================================================

    @Test
    void restore_post_shouldRedirectWithError_whenNoFileProvided() throws Exception {
        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "", "application/octet-stream", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/db-utils"))
                .andExpect(flash().attribute("errorMessage",
                        "No backup file selected."));
    }

    @Test
    void restore_post_shouldRedirectWithError_whenNotGzipData() throws Exception {
        byte[] notGzip = "this is not gzip at all".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "bad.json.gz",
                                "application/octet-stream", notGzip))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/db-utils"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void restore_post_shouldRedirectWithError_whenWrongSchemaVersion() throws Exception {
        byte[] wrongSchema = compress(emptyBackupWithVersion("9999.0"));

        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "wrong-schema.json.gz",
                                "application/octet-stream", wrongSchema))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/db-utils"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void restore_post_errorMessage_shouldContainIncompatibleVersion_whenSchemaMismatch()
            throws Exception {
        byte[] wrongSchema = compress(emptyBackupWithVersion("OLD.VERSION"));

        MvcResult result = mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "old-schema.json.gz",
                                "application/octet-stream", wrongSchema))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String errorMsg = (String) result.getFlashMap().get("errorMessage");
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("OLD.VERSION") || errorMsg.contains("Incompatible"),
                "Error message should mention the incompatible version: " + errorMsg);
    }

    @Test
    void restore_post_shouldRedirectWithError_whenGzipContainsInvalidJson() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write("{ not : valid : json ]]]".getBytes(StandardCharsets.UTF_8));
        }
        byte[] badJson = bos.toByteArray();

        mockMvc.perform(multipart("/db-utils/restore")
                        .file(new MockMultipartFile(
                                "backupFile", "bad-json.json.gz",
                                "application/octet-stream", badJson))
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String adminAuth() {
        return basicAuth("admin", "admin");
    }

    private String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getValidBackupBytes() throws Exception {
        MvcResult result = mockMvc.perform(post("/db-utils/backup")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private DatabaseBackupDTO emptyBackupWithVersion(String version) {
        return DatabaseBackupDTO.builder()
                .schemaVersion(version)
                .timestamp(LocalDateTime.now())
                .images(List.of())
                .courseTypes(List.of())
                .courseCounters(List.of())
                .trainers(List.of())
                .lecturers(List.of())
                .technicians(List.of())
                .participants(List.of())
                .courses(List.of())
                .students(List.of())
                .instructors(List.of())
                .build();
    }

    private byte[] compress(DatabaseBackupDTO dto) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            objectMapper.writeValue(gzos, dto);
        }
        return bos.toByteArray();
    }
}

