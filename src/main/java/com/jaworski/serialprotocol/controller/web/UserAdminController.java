package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.authorization.SecurityRoles;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * User administration, reachable only through the {@code /admin/**} URL rule (method security is off).
 * Passwords arrive as plain request parameters: never put one in a flash attribute, model or log line.
 */
@Controller
@RequiredArgsConstructor
public class UserAdminController {

  private static final Logger LOG = LoggerFactory.getLogger(UserAdminController.class);
  private static final String REDIRECT = "redirect:/admin/users";

  private final UserAccountService service;
  private final WebSocketPublisher webSocketPublisher;

  @GetMapping("/admin/users")
  public String users(Model model, Authentication auth) {
    model.addAttribute("users", service.list());
    model.addAttribute("me", auth.getName());
    // Lowest first: the enum's declaration order is its ranking.
    model.addAttribute("roles", List.of(SecurityRoles.values()));
    model.addAttribute("defaultRole", SecurityRoles.ROLE_USER);
    model.addAttribute(MapController.ATTRIBUTE_NAME, "admin-users");
    model.addAttribute("sessions", webSocketPublisher.openPageCount());
    return "admin/users";
  }

  @PostMapping("/admin/users/add")
  public String add(@RequestParam String username, @RequestParam String password,
                    @RequestParam String role,
                    Authentication auth, RedirectAttributes redirect) {
    return run(redirect, "User created", () -> {
      service.create(auth.getName(), username, password, role);
      return null;
    });
  }

  @PostMapping("/admin/users/roles")
  public String roles(@RequestParam UUID id, @RequestParam(required = false) Long version,
                      @RequestParam String role, Authentication auth, RedirectAttributes redirect) {
    return run(redirect, "Role saved", () -> service.setRole(auth.getName(), id, version, role));
  }

  @PostMapping("/admin/users/enabled")
  public String enabled(@RequestParam UUID id, @RequestParam(required = false) Long version,
                        @RequestParam boolean enabled, Authentication auth, RedirectAttributes redirect) {
    return run(redirect, enabled ? "User enabled" : "User disabled",
        () -> service.setEnabled(auth.getName(), id, version, enabled));
  }

  @PostMapping("/admin/users/password")
  public String password(@RequestParam UUID id, @RequestParam(required = false) Long version,
                         @RequestParam String password, Authentication auth, RedirectAttributes redirect) {
    return run(redirect, "Password reset", () -> service.resetPassword(auth.getName(), id, version, password));
  }

  /**
   * Runs one change. The action returns the username whose sessions must end, or null. They end
   * after the service method returned, so a rolled-back change logs nobody out.
   */
  private String run(RedirectAttributes redirect, String success, Supplier<String> action) {
    try {
      String expire = action.get();
      if (expire != null) {
        service.expireSessions(expire);
      }
      redirect.addFlashAttribute("successMessage", success);
    } catch (OptimisticLockingFailureException | IllegalArgumentException e) {
      redirect.addFlashAttribute("errorMessage", e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("User change failed: {}", e.getClass().getSimpleName());
      redirect.addFlashAttribute("errorMessage", "The change could not be saved.");
    }
    return REDIRECT;
  }
}
