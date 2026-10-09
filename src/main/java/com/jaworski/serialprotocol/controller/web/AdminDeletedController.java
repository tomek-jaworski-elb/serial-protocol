package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.DeletedRecordDTO;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.custom.DeletedKind;
import com.jaworski.serialprotocol.service.db.custom.DeletedRecordsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The trash: what users deleted, with restore and purge. Lives under {@code /admin/**}, which
 * {@code SecurityConfig} reserves for {@code ROLE_ADMIN}.
 */
@Controller
@RequestMapping("/admin/deleted")
@RequiredArgsConstructor
public class AdminDeletedController {

  private static final Logger LOG = LoggerFactory.getLogger(AdminDeletedController.class);
  private static final String BASE = "/admin/deleted/";

  private final DeletedRecordsService deletedRecords;
  private final WebSocketPublisher webSockerService;

  @GetMapping
  public String index() {
    return "redirect:" + BASE + DeletedKind.PARTICIPANTS.getPath();
  }

  @GetMapping("/{entity}")
  public String list(@PathVariable String entity,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "10") int size,
                     Model model) {
    DeletedKind kind = kindOr404(entity);
    Page<DeletedRecordDTO> rows = deletedRecords.list(kind, PageRequest.of(page, size));
    model.addAttribute("name", "admin-deleted");
    model.addAttribute("sessions", webSockerService.openPageCount());
    model.addAttribute("kinds", DeletedKind.values());
    model.addAttribute("kind", kind);
    model.addAttribute("baseUrl", BASE + kind.getPath());
    model.addAttribute("rows", rows.getContent());
    model.addAttribute("rowsPage", rows);
    model.addAttribute("currentPage", page);
    model.addAttribute("pageSize", size);
    return "admin-deleted";
  }

  @PostMapping("/{entity}/restore/{key}")
  public String restore(@PathVariable String entity, @PathVariable String key, RedirectAttributes redirect) {
    DeletedKind kind = kindOr404(entity);
    try {
      deletedRecords.restore(kind, key);
      redirect.addFlashAttribute("successMessage", kind.getSingular() + " restored.");
    } catch (IllegalArgumentException | IllegalStateException e) {
      LOG.warn("Cannot restore {} {}: {}", kind.getPath(), key, e.getMessage());
      redirect.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot restore {} {}", kind.getPath(), key, e);
      redirect.addFlashAttribute("errorMessage", "Failed to restore the record.");
    }
    return "redirect:" + BASE + kind.getPath();
  }

  @PostMapping("/{entity}/purge/{key}")
  public String purge(@PathVariable String entity, @PathVariable String key, RedirectAttributes redirect) {
    DeletedKind kind = kindOr404(entity);
    try {
      deletedRecords.purge(kind, key);
      redirect.addFlashAttribute("successMessage", kind.getSingular() + " permanently deleted.");
    } catch (IllegalArgumentException | IllegalStateException e) {
      LOG.warn("Cannot purge {} {}: {}", kind.getPath(), key, e.getMessage());
      redirect.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Cannot purge {} {}", kind.getPath(), key, e);
      redirect.addFlashAttribute("errorMessage", "Failed to delete the record permanently.");
    }
    return "redirect:" + BASE + kind.getPath();
  }

  private static DeletedKind kindOr404(String entity) {
    return DeletedKind.fromPath(entity)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such kind of record: " + entity));
  }
}
