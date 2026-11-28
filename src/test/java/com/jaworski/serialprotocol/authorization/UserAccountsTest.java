package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Users live in {@code app_user}: seeded once from {@code custom.server.credentials.*}, read by
 * {@link DbUserDetailsService}. Tests that change rows are {@code @Transactional}, so the shared
 * context keeps its {@code user} and {@code admin} for every other test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserAccountsTest {

  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private UserSeeder seeder;
  @Autowired
  private DbUserDetailsService userDetailsService;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private MockMvc mockMvc;

  @Test
  void theStartSeedsTheConfiguredUserAndAdminWithHashedPasswords() {
    UserAccount user = repository.findByUsername("user").orElseThrow();
    UserAccount admin = repository.findByUsername("admin").orElseThrow();

    assertThat(user.getRoles()).isEqualTo("USER");
    assertThat(admin.getRoles()).isEqualTo("ADMIN");
    assertThat(user.getPassword()).startsWith("{bcrypt}");
    assertThat(passwordEncoder.matches("user", user.getPassword())).isTrue();
    assertThat(passwordEncoder.matches("admin", admin.getPassword())).isTrue();
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.isMustChangePassword()).isFalse(); // the test property seeds unflagged accounts
    assertThat(admin.isMustChangePassword()).isFalse();
  }

  @Test
  @Transactional
  void anEmptyTableIsSeededAndASecondRunAddsNothing() {
    entityManager.createNativeQuery("DELETE FROM app_user").executeUpdate();

    seeder.seed();
    seeder.seed();

    assertThat(repository.countIncludingDeleted()).isEqualTo(2);
    assertThat(repository.findByUsername("admin")).isPresent();
  }

  @Test
  @Transactional
  void deletedAccountsAreNotRecreated() {
    repository.deleteAll();
    entityManager.flush();

    seeder.seed();
    entityManager.flush();

    assertThat(repository.count()).isZero();
    assertThat(repository.countIncludingDeleted()).isEqualTo(2);
  }

  @Test
  void theLoginIgnoresCaseAndCarriesTheRoles() {
    UserDetails admin = userDetailsService.loadUserByUsername("ADMIN");

    assertThat(admin.getUsername()).isEqualTo("admin");
    assertThat(admin.getAuthorities()).extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
    assertThat(admin.getPassword()).isEqualTo(repository.findByUsername("admin").orElseThrow().getPassword());
  }

  @Test
  void usernamesAreStoredInLowerCase() {
    UserAccount mixed = new UserAccount();
    mixed.setUsername("MiXed");

    assertThat(mixed.getUsername()).isEqualTo("mixed");
  }

  @Test
  @Transactional
  void aDeletedAccountCannotSignIn() {
    repository.delete(repository.findByUsername("user").orElseThrow());
    entityManager.flush();

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  @Transactional
  void aDisabledAccountOrAWrongPasswordIsRefused() throws Exception {
    mockMvc.perform(get("/name-service").with(httpBasic("user", "user"))).andExpect(status().isOk());
    mockMvc.perform(get("/name-service").with(httpBasic("user", "wrong"))).andExpect(status().isUnauthorized());

    repository.findByUsername("user").orElseThrow().setEnabled(false);
    entityManager.flush();

    mockMvc.perform(get("/name-service").with(httpBasic("user", "user"))).andExpect(status().isUnauthorized());
  }

  @Test
  @Transactional
  void aDisabledAccountCannotUseTheLoginForm() throws Exception {
    repository.findByUsername("user").orElseThrow().setEnabled(false);
    entityManager.flush();

    mockMvc.perform(post("/login").with(csrf()).param("username", "user").param("password", "user"))
        .andExpect(redirectedUrl("/login?error"));
  }

  @Autowired
  private org.springframework.context.ApplicationContext context;

  @Test
  void theSessionEventPublisherIsRegistered() {
    assertThat(context.getBeanNamesForType(org.springframework.security.web.session.HttpSessionEventPublisher.class))
        .hasSize(1);
  }
}
