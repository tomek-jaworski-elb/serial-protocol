package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.sync.SyncBackupService;
import com.jaworski.serialprotocol.service.db.sync.SyncExporter;
import com.jaworski.serialprotocol.service.db.sync.SyncFileNames;
import com.jaworski.serialprotocol.service.db.sync.SyncImportPreview;
import com.jaworski.serialprotocol.service.db.sync.SyncImportResult;
import com.jaworski.serialprotocol.service.db.sync.SyncImportService;
import com.jaworski.serialprotocol.service.db.sync.SyncNodeService;
import com.jaworski.serialprotocol.service.db.sync.SyncRefusedException;
import com.jaworski.serialprotocol.service.db.sync.SyncStaging;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The synchronisation page (docs/sync-spec.md): node status, export, backups, and the import
 * in two steps (upload and report, then apply). Open to every logged-in user; the admin-only actions (restore, new node id) live
 * under {@code /admin/sync}.
 */
@Controller
@RequestMapping("/sync")
public class SyncController {

  private static final Logger LOG = LoggerFactory.getLogger(SyncController.class);

  private final SyncNodeService nodeService;
  private final SyncProperties properties;
  private final WebSocketPublisher webSocketPublisher;
  private final SyncExporter exporter;
  private final SyncBackupService backups;
  private final SyncImportService imports;
  private final Clock clock;

  public SyncController(SyncNodeService nodeService, SyncProperties properties,
      WebSocketPublisher webSocketPublisher, SyncExporter exporter, SyncBackupService backups,
      SyncImportService imports, Clock clock) {
    this.nodeService = nodeService;
    this.properties = properties;
    this.webSocketPublisher = webSocketPublisher;
    this.exporter = exporter;
    this.backups = backups;
    this.imports = imports;
    this.clock = clock;
  }

  @GetMapping
  public String status(Model model) throws IOException {
    model.addAttribute("name", "sync");
    model.addAttribute("node", nodeService.current());
    model.addAttribute("syncMode", properties.getMode());
    model.addAttribute("backupDir", backups.directory().toString());
    model.addAttribute("backupKeep", properties.getBackupKeep());
    model.addAttribute("backups", backups.list());
    model.addAttribute("sessions", webSocketPublisher.openPageCount());
    model.addAttribute("maxFileSize", properties.getMaxFileSize().toGigabytes() + " GB");
    return "sync";
  }

  /**
   * Step one of the import: the file is staged on disk, analysed, and the browser is sent to
   * the report. A file that cannot be imported is discarded with the reason as a flash message.
   */
  @PostMapping("/import")
  public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes)
      throws IOException {
    if (file == null || file.isEmpty()) {
      redirectAttributes.addFlashAttribute("errorMessage", "Choose a sync file first.");
      return "redirect:/sync";
    }
    SyncStaging.Staged staged;
    try (InputStream in = file.getInputStream()) {
      staged = imports.staging().stage(in, file.getOriginalFilename());
    }
    try {
      imports.analyze(staged);
    } catch (SyncRefusedException e) {
      imports.staging().discard(staged.token());
      LOG.info("Sync file {} refused: {}", staged.originalName(), e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/sync";
    }
    return "redirect:/sync/import/" + staged.token();
  }

  /** The report for a staged file; analysed again on every view, so a refresh shows the current state. */
  @GetMapping("/import/{token}")
  public String report(@PathVariable String token, Model model, RedirectAttributes redirectAttributes) {
    SyncStaging.Staged staged = imports.staging().find(token).orElse(null);
    if (staged == null) {
      redirectAttributes.addFlashAttribute("errorMessage",
          "The uploaded file is no longer available; upload it again.");
      return "redirect:/sync";
    }
    SyncImportPreview preview;
    try {
      preview = imports.analyze(staged);
    } catch (SyncRefusedException e) {
      imports.staging().discard(token);
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/sync";
    }
    model.addAttribute("name", "sync");
    model.addAttribute("preview", preview);
    model.addAttribute("analysis", preview.analysis());
    model.addAttribute("collisionsByTable", preview.analysis().collisionsByTable());
    model.addAttribute("node", nodeService.current());
    return "sync-report";
  }

  /**
   * Step two: every form field except the CSRF token is a collision id with the chosen
   * resolution. A refusal (a decision missing, the database changed since the report) sends
   * the browser back to the report, which is analysed afresh.
   */
  @PostMapping("/import/{token}/apply")
  public String apply(@PathVariable String token, @RequestParam Map<String, String> form,
      RedirectAttributes redirectAttributes) throws IOException {
    SyncStaging.Staged staged = imports.staging().find(token).orElse(null);
    if (staged == null) {
      redirectAttributes.addFlashAttribute("errorMessage",
          "The uploaded file is no longer available; upload it again.");
      return "redirect:/sync";
    }
    Map<String, String> resolutions = new LinkedHashMap<>(form);
    resolutions.remove("_csrf");
    SyncImportResult result;
    try {
      result = imports.apply(staged, resolutions);
    } catch (SyncRefusedException e) {
      LOG.info("Sync file {} not applied: {}", staged.originalName(), e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/sync/import/" + token;
    }
    var report = result.report();
    StringBuilder message = new StringBuilder("Imported " + report.file() + ": " + report.inserted()
        + " record(s) added, " + report.updated() + " updated");
    if (!report.collisions().isEmpty()) {
      message.append(", ").append(report.collisions().size()).append(" collision(s) resolved");
    }
    message.append(". Backup: ").append(result.backup().getFileName()).append('.');
    redirectAttributes.addFlashAttribute("successMessage", message.toString());
    return "redirect:/sync";
  }

  @PostMapping("/import/{token}/discard")
  public String discard(@PathVariable String token, RedirectAttributes redirectAttributes) {
    imports.staging().discard(token);
    redirectAttributes.addFlashAttribute("successMessage", "The uploaded file was discarded; nothing was changed.");
    return "redirect:/sync";
  }

  /** The container limit is sized for a sync file, so only an oversized sync file can get here. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public RedirectView tooLarge(RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("errorMessage",
        "The sync file is larger than the allowed " + properties.getMaxFileSize().toGigabytes() + " GB.");
    return new RedirectView("/sync", true);
  }

  /**
   * Streams a fresh sync file straight into the response, so a database of any size is never
   * held in memory, and records the export once the whole file has gone out. A READ_ONLY node
   * may export too; the page warns that its file carries no edits worth passing on.
   */
  @PostMapping("/export")
  public void export(HttpServletResponse response) throws IOException {
    Instant now = Instant.now(clock);
    String fileName = SyncFileNames.exportName(nodeService.current().getName(), now, clock.getZone());
    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName).build().toString());
    exporter.export(response.getOutputStream(), false);
    response.flushBuffer();
    nodeService.markExported(now);
    LOG.info("Sync file exported: {}", fileName);
  }

  @PostMapping("/backup")
  public String backup(RedirectAttributes redirectAttributes) throws IOException {
    Path written = backups.writeBackup("manual");
    backups.prune();
    redirectAttributes.addFlashAttribute("successMessage", "Backup written: " + written.getFileName());
    return "redirect:/sync";
  }

  @GetMapping("/backup/{file}")
  public ResponseEntity<Resource> download(@PathVariable String file) {
    return backups.resolve(file)
        .map(path -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(path.getFileName().toString()).build().toString())
            .body((Resource) new FileSystemResource(path)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
