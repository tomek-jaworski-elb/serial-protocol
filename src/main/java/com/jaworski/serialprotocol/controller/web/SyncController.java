package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.sync.SyncNodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The synchronisation page (docs/sync-spec.md): node status now; export, import and backups
 * arrive in the later stages. Open to every logged-in user; the admin-only actions live under
 * {@code /admin/sync}.
 */
@Controller
@RequestMapping("/sync")
public class SyncController {

  private final SyncNodeService nodeService;
  private final SyncProperties properties;
  private final WebSocketPublisher webSocketPublisher;

  public SyncController(SyncNodeService nodeService, SyncProperties properties,
      WebSocketPublisher webSocketPublisher) {
    this.nodeService = nodeService;
    this.properties = properties;
    this.webSocketPublisher = webSocketPublisher;
  }

  @GetMapping
  public String status(Model model) {
    model.addAttribute("name", "sync");
    model.addAttribute("node", nodeService.current());
    model.addAttribute("syncMode", properties.getMode());
    model.addAttribute("backupDir", properties.getBackupDir().toAbsolutePath().toString());
    model.addAttribute("backupKeep", properties.getBackupKeep());
    model.addAttribute("sessions", webSocketPublisher.openPageCount());
    return "sync";
  }
}
