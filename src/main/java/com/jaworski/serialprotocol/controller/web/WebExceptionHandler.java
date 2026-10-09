package com.jaworski.serialprotocol.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = {CustomDBController.class, MapController.class, DbUtilsController.class,
    AdminDeletedController.class})
public class WebExceptionHandler {

  private static final String DEFAULT_REDIRECT = "redirect:/trainer-service";
  private static final Logger LOG = LoggerFactory.getLogger(WebExceptionHandler.class);

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String handleMaxUploadSizeExceeded(HttpServletRequest request,
                                             RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("errorMessage", "Selected file is too large. Maximum allowed size is 10 MB.");
    return resolveRedirect(request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public String handleConstraintViolation(ConstraintViolationException e,
                                           HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
    String violations = e.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .reduce((a, b) -> a + "; " + b)
        .orElse("Validation failed");
    LOG.warn("Constraint violation: {}", violations);
    redirectAttributes.addFlashAttribute("errorMessage", "Validation error: " + violations);
    return resolveRedirect(request);
  }

  /**
   * A form that was opened before someone else saved the same record. Raised by {@code VersionGuard} in the
   * services and by Hibernate's own version check; the controllers rethrow it so it lands here.
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public String handleStaleRecord(ObjectOptimisticLockingFailureException e,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
    LOG.warn("Stale form rejected: {}", e.getMessage());
    redirectAttributes.addFlashAttribute("errorMessage",
        "This record was changed by someone else in the meantime. Reload the page and try again.");
    return resolveRedirect(request);
  }

  private String resolveRedirect(HttpServletRequest request) {
    String referer = request.getHeader("Referer");
    if (referer == null || referer.isBlank()) {
      return DEFAULT_REDIRECT;
    }

    String contextPath = request.getContextPath();
    int contextIndex = referer.indexOf(contextPath);
    if (contextIndex < 0) {
      return DEFAULT_REDIRECT;
    }

    String pathWithQuery = referer.substring(contextIndex + contextPath.length());
    if (pathWithQuery.isBlank() || !pathWithQuery.startsWith("/")) {
      return DEFAULT_REDIRECT;
    }

    return "redirect:" + pathWithQuery;
  }
}

