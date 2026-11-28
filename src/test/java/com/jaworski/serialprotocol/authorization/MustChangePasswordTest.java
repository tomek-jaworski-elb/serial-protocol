package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every test flags {@code user} and {@code admin} inside its own transaction, which rolls back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class MustChangePasswordTest {

  private static final String NEW_PASSWORD = "a brand new password";

  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void flagBoth() {
    repository.findByUsername("user").orElseThrow().setMustChangePassword(true);
    repository.findByUsername("admin").orElseThrow().setMustChangePassword(true);
    entityManager.flush();
  }

  @Test
  void aHtmlPageNavigationIsRedirectedToTheChangePage() throws Exception {
    mockMvc.perform(get("/name-service").with(httpBasic("user", "user")).accept("text/html"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/account/password"));
  }

  @Test
  void anythingElseIsForbidden() throws Exception {
    mockMvc.perform(get("/name-service").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
    mockMvc.perform(post("/name-service").with(httpBasic("user", "user")).with(csrf()))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/instructors").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
  }

  @Test
  void theChangePageAndStaticAssetsPass() throws Exception {
    mockMvc.perform(get("/account/password").with(httpBasic("user", "user")).accept("text/html"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/css/custom-crud.css").with(httpBasic("user", "user"))).andExpect(status().isOk());
    mockMvc.perform(post("/account/password").with(httpBasic("user", "user")).with(csrf())
            .param("current", "wrong").param("newPassword", NEW_PASSWORD).param("confirm", NEW_PASSWORD))
        .andExpect(redirectedUrl("/account/password"));
  }

  @Test
  void logoutAndTheWellKnownChangePasswordUrlAreNotBlocked() throws Exception {
    // No Accept header: a blocked request would be a 403, not a redirect.
    mockMvc.perform(get("/.well-known/change-password").with(httpBasic("user", "user")))
        .andExpect(redirectedUrl("/account/password"));
    mockMvc.perform(post("/logout").with(httpBasic("user", "user")).with(csrf()))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void afterTheChangeOnlyTheNewPasswordWorks() throws Exception {
    mockMvc.perform(post("/account/password").with(httpBasic("user", "user")).with(csrf())
            .param("current", "user").param("newPassword", NEW_PASSWORD).param("confirm", NEW_PASSWORD))
        .andExpect(redirectedUrl("/name-service"));

    mockMvc.perform(get("/name-service").with(httpBasic("user", NEW_PASSWORD))).andExpect(status().isOk());
    mockMvc.perform(get("/name-service").with(httpBasic("user", "user"))).andExpect(status().isUnauthorized());
  }

  @Test
  void aFormLoginSessionIsRedirectedUntilTheOwnChangeAndThenWorksWithoutAnotherLogin() throws Exception {
    MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
            .param("username", "user").param("password", "user"))
        .andReturn().getRequest().getSession(false);
    assertThat(session).isNotNull();

    mockMvc.perform(get("/name-service").session(session).accept("text/html"))
        .andExpect(redirectedUrl("/account/password"));

    mockMvc.perform(post("/account/password").session(session).with(csrf())
            .param("current", "user").param("newPassword", NEW_PASSWORD).param("confirm", NEW_PASSWORD))
        .andExpect(redirectedUrl("/name-service"));

    mockMvc.perform(get("/name-service").session(session)).andExpect(status().isOk());
  }

  @Test
  void aFlaggedAdministratorIsBlockedFromUserAdministration() throws Exception {
    mockMvc.perform(get("/admin/users").with(httpBasic("admin", "admin")).accept("text/html"))
        .andExpect(redirectedUrl("/account/password"));

    var user = repository.findByUsername("user").orElseThrow();
    mockMvc.perform(post("/admin/users/enabled").with(httpBasic("admin", "admin")).with(csrf())
            .param("id", user.getUuid().toString()).param("enabled", "false"))
        .andExpect(status().isForbidden());
    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findByUsername("user").orElseThrow().isEnabled()).isTrue();
  }

  @Test
  void aBasicRequestForAnAssetCreatesNoSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/css/custom-crud.css").with(httpBasic("user", "user")))
        .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
  }
}
