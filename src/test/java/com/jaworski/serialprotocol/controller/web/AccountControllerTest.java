package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

  private static final String NEW_PASSWORD = "a brand new password";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void aSuccessfulChangeStoresTheNewHash() throws Exception {
    mockMvc.perform(change("user", NEW_PASSWORD, NEW_PASSWORD))
        .andExpect(redirectedUrl("/name-service"))
        .andExpect(flash().attributeExists("successMessage"));

    assertThat(passwordEncoder.matches(NEW_PASSWORD, hash())).isTrue();
  }

  @Test
  void aWrongCurrentPasswordAMismatchAndAShortPasswordLeaveTheHashAlone() throws Exception {
    String before = hash();

    mockMvc.perform(change("nope", NEW_PASSWORD, NEW_PASSWORD))
        .andExpect(redirectedUrl("/account/password")).andExpect(flash().attributeExists("errorMessage"));
    mockMvc.perform(change("user", NEW_PASSWORD, NEW_PASSWORD + "x"))
        .andExpect(redirectedUrl("/account/password")).andExpect(flash().attributeExists("errorMessage"));
    mockMvc.perform(change("user", "short", "short"))
        .andExpect(redirectedUrl("/account/password")).andExpect(flash().attributeExists("errorMessage"));

    assertThat(hash()).isEqualTo(before);
  }

  @Test
  void aPasswordErrorMessageDoesNotEchoThePassword() throws Exception {
    var result = mockMvc.perform(change("user", "tooshort", "tooshort")).andReturn();

    assertThat(result.getFlashMap().toString()).doesNotContain("tooshort");
  }

  @Test
  void anonymousIsSentToTheLogin() throws Exception {
    mockMvc.perform(get("/account/password").accept("text/html")).andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void theWellKnownUrlRedirectsToTheChangePage() throws Exception {
    mockMvc.perform(get("/.well-known/change-password")).andExpect(status().isFound())
        .andExpect(header().string("Location", endsWith("/account/password")));
  }

  @Test
  void aPostWithoutCsrfIsForbidden() throws Exception {
    mockMvc.perform(post("/account/password").with(httpBasic("user", "user"))
            .param("current", "user").param("newPassword", NEW_PASSWORD).param("confirm", NEW_PASSWORD))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder change(
      String current, String next, String confirm) {
    return post("/account/password").with(httpBasic("user", "user")).with(csrf())
        .param("current", current).param("newPassword", next).param("confirm", confirm);
  }

  private String hash() {
    return repository.findByUsername("user").orElseThrow().getPassword();
  }
}
