package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;
import java.io.IOException;
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
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The {@code /sync} page: every logged-in user may see it, nobody else; ACTIVE shows no bar. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"sync.backup-dir=target/sync-page-test", "sync.work-dir=target/sync-work-test"})
class SyncPageTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private SyncBackupService backups;

  /** A successful import or restore prunes, so the directory must not carry over the previous runs' files. */
  @BeforeEach
  void cleanDirectory() throws IOException {
    FileSystemUtils.deleteRecursively(backups.directory());
  }

  private static String auth() {
    return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
  }

  private static String adminAuth() {
    return "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());
  }

  @Test
  void onlyAnAdminSeesTheRestoreAndNewNodeIdControls() throws Exception {
    backups.writeBackup("manual");
    String asUser = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(asUser).doesNotContain("/admin/sync/").doesNotContain("syncNewNodeIdBtn");

    String asAdmin = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, adminAuth()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(asAdmin).contains("action=\"/admin/sync/restore/").contains("id=\"syncNewNodeIdBtn\"")
        .contains("action=\"/admin/sync/node-id\"");
  }

  @Test
  void restoreIsRefusedForAUserAndRunsForAnAdmin() throws Exception {
    Path backup = backups.writeBackup("manual");
    String name = backup.getFileName().toString();

    mockMvc.perform(post("/admin/sync/restore/" + name).with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/admin/sync/restore/" + name).with(csrf()).header(HttpHeaders.AUTHORIZATION, adminAuth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.startsWith("Restored " + name + ":"),
            org.hamcrest.Matchers.containsString("before-restore"))));
    assertThat(backups.list().getFirst().name()).contains("before-restore");

    mockMvc.perform(post("/admin/sync/restore/missing.bak").with(csrf()).header(HttpHeaders.AUTHORIZATION, adminAuth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  void aNewNodeIdIsAnAdminActionThatKeepsTheName() throws Exception {
    mockMvc.perform(post("/admin/sync/node-id").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isForbidden());

    String before = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsString();
    String oldId = between(before, "id=\"syncNodeId\">", "<");
    String oldName = between(before, "id=\"syncNodeName\">", "<");

    mockMvc.perform(post("/admin/sync/node-id").with(csrf()).header(HttpHeaders.AUTHORIZATION, adminAuth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.startsWith("This installation now has node id ")));

    String after = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsString();
    assertThat(between(after, "id=\"syncNodeId\">", "<")).isNotEqualTo(oldId).hasSize(36);
    assertThat(between(after, "id=\"syncNodeName\">", "<")).isEqualTo(oldName);
  }

  private static String between(String html, String start, String end) {
    int from = html.indexOf(start) + start.length();
    return html.substring(from, html.indexOf(end, from));
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

  /** Our own export, re-labelled as another node's, so the two-step import can be walked end to end. */
  private byte[] fileFromAnotherNode() throws Exception {
    byte[] export = mockMvc.perform(post("/sync/export").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsByteArray();
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(export))) {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8)
        .replaceAll("\"id\" : \"[0-9a-f-]+\"", "\"id\" : \"" + UUID.randomUUID() + "\"")
        .replace("\"name\" : \"", "\"name\" : \"Laptop-");
    entries.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
    return zip(entries);
  }

  private static byte[] zip(Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  @Test
  void importStagesTheFileAndShowsTheReportWithoutChangingAnything() throws Exception {
    MockMultipartFile upload = new MockMultipartFile("file", "laptop.sync", "application/zip", fileFromAnotherNode());

    String location = mockMvc.perform(multipart("/sync/import").file(upload).with(csrf())
            .header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/sync/import/*"))
        .andReturn().getResponse().getRedirectedUrl();

    String html = mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(html).contains("id=\"syncReportFile\"").contains("laptop.sync").contains("Laptop-");
    assertThat(html).contains("<strong id=\"syncReportCollisions\">0</strong>");
    assertThat(html).contains("id=\"syncApplyBtn\" type=\"submit\" class=\"btn btn-primary\"").doesNotContain("syncApplyBtn\" type=\"submit\" class=\"btn btn-primary\" disabled");
    assertThat(html).doesNotContain("syncCollision\"");

    mockMvc.perform(post(location + "/discard").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"));
    mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  void applyingTheReportLoadsTheFileWritesABackupAndDiscardsTheUpload() throws Exception {
    MockMultipartFile upload = new MockMultipartFile("file", "laptop.sync", "application/zip", fileFromAnotherNode());
    String location = mockMvc.perform(multipart("/sync/import").file(upload).with(csrf())
            .header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getRedirectedUrl();
    int backupsBefore = backups.list().size();

    mockMvc.perform(post(location + "/apply").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.startsWith("Imported laptop.sync:"),
            org.hamcrest.Matchers.containsString("Backup: "))));

    assertThat(backups.list()).hasSize(backupsBefore + 1);
    Path backup = backups.directory().resolve(backups.list().getFirst().name());
    assertThat(backup.getFileName().toString()).contains("before-import");
    assertThat(SyncBackupService.reportFor(backup)).exists();
    mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attributeExists("errorMessage"));
    mockMvc.perform(post(location + "/apply").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  void ourOwnExportIsRefusedWithAMessage() throws Exception {
    byte[] export = mockMvc.perform(post("/sync/export").with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsByteArray();
    MockMultipartFile upload = new MockMultipartFile("file", "own.sync", "application/zip", export);

    mockMvc.perform(multipart("/sync/import").file(upload).with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("our own export")));
  }

  @Test
  void somethingThatIsNotASyncFileIsRefused() throws Exception {
    MockMultipartFile upload = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

    mockMvc.perform(multipart("/sync/import").file(upload).with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"))
        .andExpect(flash().attributeExists("errorMessage"));
    mockMvc.perform(get("/sync/import/not-a-token").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(redirectedUrl("/sync"));
  }

  @Test
  void theStatusPageOffersTheImportForm() throws Exception {
    String html = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn().getResponse().getContentAsString();
    assertThat(html).contains("id=\"syncImportForm\"").contains("enctype=\"multipart/form-data\"");
  }
}
