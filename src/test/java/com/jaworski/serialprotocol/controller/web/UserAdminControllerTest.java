package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class UserAdminControllerTest {

  private static final String PASSWORD = "correct horse battery";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;

  @Test
  void onlyAnAdministratorSeesThePage() throws Exception {
    mockMvc.perform(get("/admin/users").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/users").accept("text/html")).andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
    mockMvc.perform(get("/admin/users").with(httpBasic("admin", "admin"))).andExpect(status().isOk());
  }

  @Test
  void thePageShowsNeitherAHashNorAPassword() throws Exception {
    String body = mockMvc.perform(get("/admin/users").with(httpBasic("admin", "admin")))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).contains("admin").doesNotContain("{bcrypt}").doesNotContain("$2a$")
        .doesNotContain(repository.findByUsername("admin").orElseThrow().getPassword());
  }

  @Test
  void addCreatesTheUser() throws Exception {
    mockMvc.perform(admin(post("/admin/users/add")).param("username", "Bob").param("password", PASSWORD)
            .param("role", "ADMIN"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));

    UserAccount bob = repository.findByUsername("bob").orElseThrow();
    assertThat(bob.getRoles()).isEqualTo("ADMIN");
    assertThat(bob.isMustChangePassword()).isTrue();
  }

  @Test
  void aWeakPasswordIsAToastThatDoesNotContainIt() throws Exception {
    var result = mockMvc.perform(admin(post("/admin/users/add")).param("username", "bob")
            .param("password", "weakpass").param("role", "USER"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"))
        .andReturn();

    assertThat(result.getFlashMap().toString()).doesNotContain("weakpass");
    assertThat(repository.findByUsername("bob")).isEmpty();
  }

  @Test
  void rolesEnabledAndPasswordPostsFlashAndRedirect() throws Exception {
    UserAccount bob = bob();
    String id = bob.getUuid().toString();
    String version = String.valueOf(bob.getVersion());

    mockMvc.perform(admin(post("/admin/users/roles")).param("id", id).param("version", version)
            .param("role", "ADMIN"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));
    entityManager.flush();
    String next = String.valueOf(repository.findById(bob.getUuid()).orElseThrow().getVersion());

    mockMvc.perform(admin(post("/admin/users/enabled")).param("id", id).param("version", next)
            .param("enabled", "false"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));
    entityManager.flush();
    next = String.valueOf(repository.findById(bob.getUuid()).orElseThrow().getVersion());

    mockMvc.perform(admin(post("/admin/users/password")).param("id", id).param("version", next)
            .param("password", "another password!"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));

    UserAccount stored = repository.findById(bob.getUuid()).orElseThrow();
    assertThat(stored.getRoles()).isEqualTo("ADMIN");
    assertThat(stored.isEnabled()).isFalse();
  }

  @Test
  void aStaleVersionAndASelfDisableAreToasts() throws Exception {
    UserAccount bob = bob();
    mockMvc.perform(admin(post("/admin/users/enabled")).param("id", bob.getUuid().toString())
            .param("version", "99").param("enabled", "false"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));

    UserAccount self = repository.findByUsername("admin").orElseThrow();
    mockMvc.perform(admin(post("/admin/users/enabled")).param("id", self.getUuid().toString())
            .param("enabled", "false"))
        .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
    assertThat(repository.findById(self.getUuid()).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  void thePageRendersTheFormsAndARowPerUser() throws Exception {
    String body = mockMvc.perform(get("/admin/users").with(httpBasic("admin", "admin")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    assertThat(body).contains("action=\"/admin/users/add\"", "action=\"/admin/users/password\"",
        "href=\"/account/password\"", "href=\"/admin/users\"",
        "data-username=\"user\"", "More actions for user");
    // Own row: neither the dropdown nor the reset button.
    assertThat(body).doesNotContain("More actions for admin", "data-username=\"admin\"");
  }

  @Test
  void theUsersLinkIsForAdministratorsOnly() throws Exception {
    String body = mockMvc.perform(get("/account/password").with(httpBasic("user", "user")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    assertThat(body).contains("href=\"/account/password\"").doesNotContain("href=\"/admin/users\"")
        .contains("name=\"current\"", "name=\"newPassword\"", "name=\"confirm\"");
  }

  @Test
  void aPostWithoutCsrfIsForbidden() throws Exception {
    mockMvc.perform(post("/admin/users/add").with(httpBasic("admin", "admin"))
            .param("username", "bob").param("password", PASSWORD))
        .andExpect(status().isForbidden());
  }

  private UserAccount bob() {
    UserAccount bob = new UserAccount();
    bob.setUsername("bob");
    bob.setPassword("{bcrypt}placeholder");
    bob.setRoles("USER");
    repository.saveAndFlush(bob);
    return bob;
  }

  private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
    return builder.with(httpBasic("admin", "admin")).with(csrf());
  }
}
