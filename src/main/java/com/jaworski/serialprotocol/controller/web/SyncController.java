package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.sync.SyncBackupService;
import com.jaworski.serialprotocol.service.db.sync.SyncExporter;
import com.jaworski.serialprotocol.service.db.sync.SyncFileNames;
import com.jaworski.serialprotocol.service.db.sync.SyncNodeService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

/**
 * The synchronisation page (docs/sync-spec.md): node status, export, backups. Import arrives
 * in stage 3. Open to every logged-in user; the admin-only actions (restore, new node id) live
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
  private final Clock clock;

  public SyncController(SyncNodeService nodeService, SyncProperties properties,
      WebSocketPublisher webSocketPublisher, SyncExporter exporter, SyncBackupService backups, Clock clock) {
    this.nodeService = nodeService;
    this.properties = properties;
    this.webSocketPublisher = webSocketPublisher;
    this.exporter = exporter;
    this.backups = backups;
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
    return "sync";
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
