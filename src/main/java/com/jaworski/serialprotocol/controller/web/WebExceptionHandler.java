package com.jaworski.serialprotocol.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = {CustomDBController.class, MapController.class})
public class WebExceptionHandler {

  private static final String DEFAULT_REDIRECT = "redirect:/trainer-service";

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String handleMaxUploadSizeExceeded(HttpServletRequest request,
                                             RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("errorMessage", "Selected file is too large. Maximum allowed size is 10 MB.");

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

