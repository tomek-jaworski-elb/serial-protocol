package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.custom.SyncNode;
import com.jaworski.serialprotocol.service.db.sync.SyncBackupService;
import com.jaworski.serialprotocol.service.db.sync.SyncNodeService;
import com.jaworski.serialprotocol.service.db.sync.SyncRefusedException;
import com.jaworski.serialprotocol.service.db.sync.SyncRestoreResult;
import com.jaworski.serialprotocol.service.db.sync.SyncRestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The two synchronisation actions that undo rather than merge (docs/sync-spec.md § Backups):
 * restoring a backup in replace mode and giving the installation a new node id. Under
 * {@code /admin/**}, so {@code ROLE_ADMIN} through the security rule rather than a check here;
 * the page shows the controls to admins only.
 */
@Controller
@RequestMapping("/admin/sync")
public class SyncAdminController {

  private static final Logger LOG = LoggerFactory.getLogger(SyncAdminController.class);

  private final SyncBackupService backups;
  private final SyncRestoreService restores;
  private final SyncNodeService nodeService;

  public SyncAdminController(SyncBackupService backups, SyncRestoreService restores, SyncNodeService nodeService) {
    this.backups = backups;
    this.restores = restores;
    this.nodeService = nodeService;
  }

  @PostMapping("/restore/{file}")
  public String restore(@PathVariable String file, RedirectAttributes redirectAttributes) throws IOException {
    Path backup = backups.resolve(file).orElse(null);
    if (backup == null) {
      redirectAttributes.addFlashAttribute("errorMessage", "There is no backup named " + file + ".");
      return "redirect:/sync";
    }
    try {
      SyncRestoreResult result = restores.restore(backup);
      redirectAttributes.addFlashAttribute("successMessage", "Restored " + result.restored() + ": "
          + result.rows() + " row(s) and " + result.blobs() + " photo(s) loaded. The state before it is in "
          + result.beforeRestore().getFileName() + ".");
    } catch (SyncRefusedException e) {
      LOG.info("Backup {} not restored: {}", file, e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/sync";
  }

  @PostMapping("/node-id")
  public String renewNodeId(RedirectAttributes redirectAttributes) {
    SyncNode fresh = nodeService.renewId();
    redirectAttributes.addFlashAttribute("successMessage", "This installation now has node id " + fresh.getId()
        + "; its sync history starts over.");
    return "redirect:/sync";
  }
}
