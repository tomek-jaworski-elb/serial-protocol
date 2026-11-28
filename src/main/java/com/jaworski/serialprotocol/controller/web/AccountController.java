package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.authorization.LandingPage;
import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.db.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

/** The signed-in user's own password change. Passwords are plain request parameters, never logged or flashed. */
@Controller
@RequiredArgsConstructor
public class AccountController {

  private static final Logger LOG = LoggerFactory.getLogger(AccountController.class);
  private static final String CHANGE_PAGE = "redirect:/account/password";

  private final UserAccountService service;
  private final UserAccountRepository repository;
  private final WebSocketPublisher webSocketPublisher;
  private final LandingPage landingPage;

  @GetMapping("/account/password")
  public String page(Model model, Authentication auth) {
    boolean forced = repository.findByUsername(UserAccount.normalize(auth.getName()))
        .map(UserAccount::isMustChangePassword).orElse(false);
    model.addAttribute("forced", forced);
    model.addAttribute(MapController.ATTRIBUTE_NAME, "account-password");
    model.addAttribute("sessions", webSocketPublisher.openPageCount());
    return "account/password";
  }

  @PostMapping("/account/password")
  public String change(@RequestParam String current, @RequestParam String newPassword,
                       @RequestParam String confirm, Authentication auth,
                       HttpServletRequest request, RedirectAttributes redirect) {
    if (!newPassword.equals(confirm)) {
      redirect.addFlashAttribute("errorMessage", "The new password and its confirmation differ");
      return CHANGE_PAGE;
    }
    String username = auth.getName();
    try {
      service.changeOwnPassword(username, current, newPassword);
    } catch (OptimisticLockingFailureException | IllegalArgumentException e) {
      redirect.addFlashAttribute("errorMessage", e.getMessage());
      return CHANGE_PAGE;
    } catch (RuntimeException e) {
      LOG.error("Own password change failed: {}", e.getClass().getSimpleName());
      redirect.addFlashAttribute("errorMessage", "The password could not be changed.");
      return CHANGE_PAGE;
    }
    // Never getSession(): a Basic client has no session and must not get one.
    HttpSession session = request.getSession(false);
    String oldId = null;
    String newId = null;
    if (session != null) {
      oldId = session.getId();
      newId = request.changeSessionId();
    }
    // Both ids are kept: the registry may not have re-keyed to the new id yet.
    service.expireSessions(username, oldId, newId);
    redirect.addFlashAttribute("successMessage", "Password changed");
    return "redirect:" + landingPage.urlFor(auth);
  }

  @GetMapping("/.well-known/change-password")
  public String wellKnown() {
    return CHANGE_PAGE;
  }
}
