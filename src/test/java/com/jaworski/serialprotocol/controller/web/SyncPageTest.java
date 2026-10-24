package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The {@code /sync} page: every logged-in user may see it, nobody else; ACTIVE shows no bar. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SyncPageTest {

  @Autowired
  private MockMvc mockMvc;

  private static String auth() {
    return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
  }

  @Test
  void aLoggedInUserSeesTheStatusPageWithoutTheReadOnlyBar() throws Exception {
    String html = mockMvc.perform(get("/sync").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(html).contains("id=\"syncNodeId\"").contains(">ACTIVE<");
    assertThat(html).doesNotContain("syncReadOnlyBar");
    assertThat(html).contains("href=\"/sync\"");
  }

  @Test
  void anAnonymousVisitorIsRefused() throws Exception {
    mockMvc.perform(get("/sync")).andExpect(status().isUnauthorized());
  }

  @Test
  void anOrdinaryPageCarriesNoBarInActiveMode() throws Exception {
    String html = mockMvc.perform(get("/trainer-service").header(HttpHeaders.AUTHORIZATION, auth()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(html).doesNotContain("syncReadOnlyBar");
  }
}
