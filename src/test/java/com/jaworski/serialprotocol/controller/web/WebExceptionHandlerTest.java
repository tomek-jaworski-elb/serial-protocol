package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where an error sends the user: back to the page the form came from, and never off this host. */
class WebExceptionHandlerTest {

  private static final String DEFAULT = "redirect:/trainer-service";

  @ParameterizedTest(name = "[{0}] referer {1} -> {2}")
  @CsvSource(nullValues = "NULL", value = {
      "'',    https://localhost/course-type-service?page=0&size=10, redirect:/course-type-service?page=0&size=10",
      "'',    https://localhost:8081/admin/deleted/trainers,          redirect:/admin/deleted/trainers",
      "'',    /participant-service,                                   redirect:/participant-service",
      "'',    https://localhost/,                                     redirect:/",
      "'',    NULL,                                                   " + DEFAULT,
      "'',    '   ',                                                  " + DEFAULT,
      "'',    https://localhost,                                      " + DEFAULT,
      "'',    https://localhost//evil.example/x,                      " + DEFAULT,
      "'',    https://localhost/a b,                                  " + DEFAULT,
      "/app,  https://localhost/app/trainer-service?page=1,           redirect:/trainer-service?page=1",
      "/app,  https://localhost/application/x,                        " + DEFAULT,
      "/app,  https://localhost/other,                                " + DEFAULT,
  })
  void redirectsToTheRefererPathOnly(String contextPath, String referer, String expected) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContextPath(contextPath);
    if (referer != null) {
      request.addHeader("Referer", referer);
    }

    String view = new WebExceptionHandler().handleStaleRecord(
        new ObjectOptimisticLockingFailureException(Object.class, 1L), request, new RedirectAttributesModelMap());

    assertEquals(expected, view);
  }
}
