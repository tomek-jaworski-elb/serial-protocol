package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An admin change ends the user's sessions; the next request of each lands on {@code /login?expired}.
 * The registry is a shared singleton and not rolled back, so every entry is removed afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class SessionExpiryTest {

  private static final String NEW_PASSWORD = "a brand new password";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private SessionRegistry registry;
  @Autowired
  private UserAccountRepository repository;

  @AfterEach
  void clearRegistry() {
    for (Object principal : registry.getAllPrincipals()) {
      registry.getAllSessions(principal, true).forEach(s -> registry.removeSessionInformation(s.getSessionId()));
    }
  }

  @Test
  void aDisableEndsTheSessionsOfThatUserOnly() throws Exception {
    MockHttpSession user = login("user", "user");
    MockHttpSession admin = login("admin", "admin");

    adminPost("/admin/users/enabled", "enabled", "false");

    assertThat(registry.getSessionInformation(user.getId()).isExpired()).isTrue();
    assertThat(registry.getSessionInformation(admin.getId()).isExpired()).isFalse();
    mockMvc.perform(get("/name-service").session(user)).andExpect(redirectedUrl("/login?expired"));
  }

  @Test
  void aRoleChangeEndsTheSession() throws Exception {
    MockHttpSession user = login("user", "user");

    adminPost("/admin/users/roles", "role", "ADMIN");

    assertThat(registry.getSessionInformation(user.getId()).isExpired()).isTrue();
    mockMvc.perform(get("/name-service").session(user)).andExpect(redirectedUrl("/login?expired"));
  }

  @Test
  void aDemotionEndsTheSession() throws Exception {
    MockHttpSession user = login("user", "user");

    adminPost("/admin/users/roles", "role", "OPERATOR");

    assertThat(registry.getSessionInformation(user.getId()).isExpired()).isTrue();
    mockMvc.perform(get("/name-service").session(user)).andExpect(redirectedUrl("/login?expired"));
  }

  @Test
  void aNoOpRoleSaveLogsNobodyOut() throws Exception {
    MockHttpSession user = login("user", "user");

    adminPost("/admin/users/roles", "role", "USER");

    assertThat(registry.getSessionInformation(user.getId()).isExpired()).isFalse();
  }

  @Test
  void anAdminResetEndsTheSession() throws Exception {
    MockHttpSession user = login("user", "user");

    adminPost("/admin/users/password", "password", "another password!");

    assertThat(registry.getSessionInformation(user.getId()).isExpired()).isTrue();
  }

  @Test
  void anOwnChangeKeepsTheActingSessionAndEndsTheOther() throws Exception {
    MockHttpSession acting = login("user", "user");
    MockHttpSession other = login("user", "user");

    mockMvc.perform(post("/account/password").session(acting).with(csrf())
            .param("current", "user").param("newPassword", NEW_PASSWORD).param("confirm", NEW_PASSWORD))
        .andExpect(redirectedUrl("/name-service"));

    assertThat(registry.getSessionInformation(other.getId()).isExpired()).isTrue();
    mockMvc.perform(get("/name-service").session(acting)).andExpect(status().isOk());
    mockMvc.perform(get("/name-service").session(other)).andExpect(redirectedUrl("/login?expired"));
  }

  @Test
  void aLoginCarryingTheExpiredCookieStillSucceeds() throws Exception {
    MockHttpSession stale = login("user", "user");
    adminPost("/admin/users/roles", "role", "ADMIN");

    // MockMvc does not bounce this POST to /login?expired (the real container is unverified): the login succeeds.
    mockMvc.perform(loginRequest("user", "user").session(stale)).andExpect(status().isFound())
        .andExpect(redirectedUrl("/name-service"))
        .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("expired"));
  }

  private MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession) mockMvc.perform(loginRequest(username, password))
        .andReturn().getRequest().getSession(false);
  }

  private static MockHttpServletRequestBuilder loginRequest(String username, String password) {
    return post("/login").with(csrf()).param("username", username).param("password", password);
  }

  /** The admin acts on the seeded {@code user}, over Basic so the admin's own session count stays out of it. */
  private void adminPost(String url, String name, String value) throws Exception {
    var user = repository.findByUsername("user").orElseThrow();
    mockMvc.perform(post(url).with(httpBasic("admin", "admin")).with(csrf())
            .param("id", user.getUuid().toString()).param(name, value))
        .andExpect(redirectedUrl("/admin/users"));
  }
}
