package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import com.jaworski.serialprotocol.service.db.UserAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may open what: OPERATOR replays tracks only, USER adds the CRUD pages, ADMIN everything (role hierarchy). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class RoleMatrixTest {

  private static final String PASSWORD = "correct horse battery";
  private static final Set<Integer> DENIED = Set.of(302, 401, 403);

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private UserAccountService service;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private LandingPage landingPage;

  @BeforeEach
  void operator() {
    service.create("admin", "op", PASSWORD, "OPERATOR");
    entityManager.flush();
  }

  private void settled() {
    repository.findByUsername("op").orElseThrow().setMustChangePassword(false);
    entityManager.flush();
  }

  private boolean allowed(String who, Supplier<MockHttpServletRequestBuilder> supplier) throws Exception {
    MockHttpServletRequestBuilder request = supplier.get();
    if (who != null) {
      request.with(httpBasic(who, who.equals("op") ? PASSWORD : who));
    }
    return !DENIED.contains(mockMvc.perform(request).andReturn().getResponse().getStatus());
  }

  /** One row: the request and whether operator, user, admin and anonymous may send it. */
  private void row(String what, Supplier<MockHttpServletRequestBuilder> request, boolean operator, boolean user, boolean admin, boolean anonymous)
      throws Exception {
    assertThat(allowed("op", request)).as("operator " + what).isEqualTo(operator);
    assertThat(allowed("user", request)).as("user " + what).isEqualTo(user);
    assertThat(allowed("admin", request)).as("admin " + what).isEqualTo(admin);
    assertThat(allowed(null, request)).as("anonymous " + what).isEqualTo(anonymous);
  }

  @Test
  void theMatrix() throws Exception {
    settled();
    row("GET /tracks", () -> get("/tracks"), true, true, true, false);
    row("GET /tracks/api/days", () -> get("/tracks/api/days"), true, true, true, false);
    row("POST /tracks/pdf", () -> post("/tracks/pdf").with(csrf()), false, true, true, false);
    row("GET /tracks/x", () -> get("/tracks/x"), false, true, true, false);
    row("GET /name-service", () -> get("/name-service"), false, true, true, false);
    row("GET /instructor-service", () -> get("/instructor-service"), false, true, true, false);
    // Sub-paths too: the rules are "/x/**", like every other *-service page.
    row("GET /name-service/x", () -> get("/name-service/x"), false, true, true, false);
    row("GET /instructor-service/x", () -> get("/instructor-service/x"), false, true, true, false);
    row("GET /courses-service", () -> get("/courses-service"), false, true, true, false);
    row("GET /custom/image/{uuid}", () -> get("/custom/image/" + UUID.randomUUID()), false, true, true, false);
    row("GET /pdf/x", () -> get("/pdf/x"), false, true, true, false);
    row("GET /api/x", () -> get("/api/x"), false, true, true, false);
    row("GET /admin/users", () -> get("/admin/users"), false, false, true, false);
    row("GET /account/password", () -> get("/account/password"), true, true, true, false);
    row("GET /chart", () -> get("/chart"), true, true, true, true);
  }

  @Test
  void anAdminIsStoredAsAdminAloneYetReachesTheUserPages() throws Exception {
    assertThat(repository.findByUsername("admin").orElseThrow().getRoles()).isEqualTo("ADMIN");
    assertThat(allowed("admin", () -> get("/name-service"))).isTrue();
  }

  @Test
  void anOperatorsFirstLoginLandsOnTheTracksAfterTheForcedChange() throws Exception {
    MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
            .param("username", "op").param("password", PASSWORD))
        .andExpect(redirectedUrl("/tracks")).andReturn().getRequest().getSession(false);

    mockMvc.perform(get("/tracks").session(session).accept("text/html")).andExpect(redirectedUrl("/account/password"));
    mockMvc.perform(post("/account/password").session(session).with(csrf())
            .param("current", PASSWORD).param("newPassword", "another long password").param("confirm", "another long password"))
        .andExpect(redirectedUrl("/tracks"));
  }

  @Test
  void aSavedPageTheOperatorMayNotOpenIsDroppedForTheTracks() throws Exception {
    settled();
    MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/name-service").accept("text/html"))
        .andExpect(redirectedUrl("/login")).andReturn().getRequest().getSession(false);

    mockMvc.perform(post("/login").session(session).with(csrf()).param("username", "op").param("password", PASSWORD))
        .andExpect(redirectedUrl("/tracks"));

    // The same bounce returns a user to the page.
    MockHttpSession other = (MockHttpSession) mockMvc.perform(get("/name-service").accept("text/html"))
        .andReturn().getRequest().getSession(false);
    mockMvc.perform(post("/login").session(other).with(csrf()).param("username", "user").param("password", "user"))
        .andExpect(redirectedUrl("http://localhost/name-service?continue"));
  }

  @Test
  void theKeyCellsAreServedOrForbiddenExactly() throws Exception {
    settled();
    mockMvc.perform(get("/tracks").with(httpBasic("op", PASSWORD))).andExpect(status().isOk());
    mockMvc.perform(get("/tracks/api/days").with(httpBasic("op", PASSWORD))).andExpect(status().isOk());
    mockMvc.perform(get("/name-service").with(httpBasic("op", PASSWORD))).andExpect(status().isForbidden());
    mockMvc.perform(post("/tracks/pdf").with(httpBasic("op", PASSWORD)).with(csrf())).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/users").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
  }

  /** LandingPage mirrors the URL rules by hand: for every listed page it must agree with the real answer. */
  @Test
  void landingPageAgreesWithTheUrlRules() throws Exception {
    settled();
    List<String> pages = List.of("/tracks", "/tracks/", "/tracks/api/days", "/tracks/api", "/tracks/x", "/name-service", "/instructor-service",
        "/name-service/x", "/instructor-service/x", "/courses-service", "/custom/image/x", "/pdf/x", "/api/x", "/admin/users", "/admin", "/administrator", "/chart", "/account/password");
    for (String page : pages) {
      for (String who : List.of("op", "user", "admin")) {
        Authentication auth = new UsernamePasswordAuthenticationToken(who, "n/a",
            AuthorityUtils.createAuthorityList("ROLE_" + (who.equals("op") ? "OPERATOR" : who.toUpperCase())));
        assertThat(landingPage.canOpen(page, auth)).as(who + " " + page).isEqualTo(allowed(who, () -> get(page)));
      }
    }
  }

  @Test
  void aSavedAdminPageIsDroppedForAUserAndAMalformedSavedQueryStillLogsIn() throws Exception {
    MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/admin/users").accept("text/html"))
        .andReturn().getRequest().getSession(false);
    mockMvc.perform(post("/login").session(session).with(csrf()).param("username", "user").param("password", "user"))
        .andExpect(redirectedUrl("/name-service"));

    MockHttpSession bad = (MockHttpSession) mockMvc.perform(get("/name-service?a=%zz").accept("text/html"))
        .andReturn().getRequest().getSession(false);
    mockMvc.perform(post("/login").session(bad).with(csrf()).param("username", "user").param("password", "user"))
        .andExpect(status().isFound());
  }
}
