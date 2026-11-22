package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import com.jaworski.serialprotocol.service.db.UserAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

/** What each role sees in the rendered pages: the menu, the track page and the user panel. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class RoleRenderingTest {

  private static final String PASSWORD = "correct horse battery";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private UserAccountService service;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void operator() {
    service.create("admin", "op", PASSWORD, "OPERATOR");
    entityManager.flush();
    repository.findByUsername("op").orElseThrow().setMustChangePassword(false);
    entityManager.flush();
  }

  private String page(String url, String who) throws Exception {
    return mockMvc.perform(get(url).with(httpBasic(who, who.equals("op") ? PASSWORD : who)))
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  void anOperatorsTrackPageHasNoPrintSectionButKeepsWhatTheScriptNeeds() throws Exception {
    String html = page("/tracks", "op");
    assertThat(html).doesNotContain("id=\"pdfForm\"", "id=\"printBtn\"", "id=\"printFrameBtn\"", "id=\"removeFrameBtn\"");
    assertThat(html).contains("id=\"clearBtn\"", "id=\"resetBtn\"", "id=\"drawHint\"", "id=\"replayScrubber\"");
  }

  @Test
  void aUsersTrackPageHasThePrintSection() throws Exception {
    assertThat(page("/tracks", "user")).contains("id=\"pdfForm\"", "id=\"printBtn\"", "id=\"printFrameBtn\"", "id=\"removeFrameBtn\"");
  }

  @Test
  void anOperatorsMenuHasTheTracksAndNoCrudLinks() throws Exception {
    String html = page("/tracks", "op");
    assertThat(html).contains("href=\"/tracks\"", "Role: Operator");
    assertThat(html).doesNotContain("/name-service", "/instructor-service", "/admin/users", "/courses-service", "/trainer-service", "/participant-service", "/lecturer-service", "/technician-service", "/course-type-service", "/course-counter-service");
  }

  @Test
  void aStoredAdminSeesTheCrudLinksThroughTheHierarchy() throws Exception {
    assertThat(repository.findByUsername("admin").orElseThrow().getRoles()).isEqualTo("ADMIN");
    String html = page("/tracks", "admin");
    assertThat(html).contains("/name-service", "/instructor-service", "/admin/users", "Role: Admin");
  }

  @Test
  void aUserSeesTheCrudLinksButNotTheAdminPanel() throws Exception {
    String html = page("/tracks", "user");
    assertThat(html).contains("/name-service", "/instructor-service", "Role: User");
    assertThat(html).doesNotContain("/admin/users");
  }

  @Test
  void theUserPanelHasTheRoleSelectAndOneFormPerOtherRole() throws Exception {
    String html = page("/admin/users", "admin");
    assertThat(html).contains("name=\"role\"", "<option value=\"OPERATOR\">", "<option value=\"ADMIN\">");
    // the operator row offers User and Admin, never its own role
    String row = html.substring(html.indexOf("<td>op</td>"));
    row = row.substring(0, row.indexOf("</tr>"));
    assertThat(row).contains("Set role: User", "Set role: Admin").doesNotContain("Set role: Operator");
  }

  @Test
  void theRoleOptionsComeFromTheModelLowestFirstWithUserPreselected() throws Exception {
    mockMvc.perform(get("/admin/users").with(httpBasic("admin", "admin")))
        .andExpect(model().attribute("roles", List.of(SecurityRoles.values())))
        .andExpect(model().attribute("defaultRole", SecurityRoles.ROLE_USER));

    String html = page("/admin/users", "admin");
    String select = html.substring(html.indexOf("id=\"addUserRole\""), html.indexOf("</select>")).replaceAll("\\s+", " ");
    assertThat(select).containsSubsequence(
        "<option value=\"OPERATOR\">Operator</option>",
        "<option value=\"USER\" selected=\"selected\">User</option>",
        "<option value=\"ADMIN\">Admin</option>");
  }
}
