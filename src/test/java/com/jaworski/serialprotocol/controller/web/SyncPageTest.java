package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import com.jaworski.serialprotocol.service.db.sync.SyncBackupService;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The {@code /sync} page: every logged-in user may see it, nobody else; ACTIVE shows no bar. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "sync.backup-dir=target/sync-page-test")
class SyncPageTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private SyncBackupService backups;

  private static String auth() {
    return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
  }

  @Test
  void aLoggedInUserSeesTheStatusPageWithoutTheReadOnlyBar() throws Exception {
    String html = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(html).contains("id=\"syncNodeId\"").contains(">ACTIVE<");
    assertThat(html).doesNotContain("syncReadOnlyBar");
    assertThat(html).contains("href=\"/sync\"");
  }

  @Test
  void anAnonymousVisitorIsRefused() throws Exception {
    mockMvc.perform(get("/sync")).andExpect(status().isUnauthorized());
  }

  @Test
  void anOrdinaryPageCarriesNoBarInActiveMode() throws Exception {
    String html = mockMvc.perform(get("/trainer-service").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(html).doesNotContain("syncReadOnlyBar");
  }

  @Test
  void exportDownloadsASyncFileAndRecordsTheExport() throws Exception {
    var response = mockMvc.perform(post("/sync/export").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse();

    assertThat(response.getContentType()).isEqualTo("application/zip");
    assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains(".sync");
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertThat(zip.getNextEntry().getName()).isEqualTo("manifest.json");
    }
    String html = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsString();
    assertThat(html).doesNotContain("<dd class=\"col-sm-9\">never</dd>\n                <dt class=\"col-sm-3\">Last import");
  }

  @Test
  void backUpNowWritesAFileAndListsItForDownload() throws Exception {
    mockMvc.perform(post("/sync/backup").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().is3xxRedirection());

    var files = backups.list();
    assertThat(files).isNotEmpty();
    String name = files.get(0).name();
    String html = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsString();
    assertThat(html).contains("id=\"syncBackupTable\"").contains(name);

    byte[] bytes = mockMvc.perform(get("/sync/backup/" + name).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    Path path = backups.directory().resolve(name);
    assertThat(bytes).isEqualTo(Files.readAllBytes(path));
    mockMvc.perform(get("/sync/backup/nothing.bak").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isNotFound());
  }
}
