package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the synchronisation mode into every model so {@code fragment.html} can show the
 * read-only bar on every page and turn a successful save into a warning toast.
 */
@ControllerAdvice
public class SyncModelAdvice {

  private final SyncProperties properties;

  public SyncModelAdvice(SyncProperties properties) {
    this.properties = properties;
  }

  @ModelAttribute("syncReadOnly")
  public boolean syncReadOnly() {
    return properties.isReadOnly();
  }
}
