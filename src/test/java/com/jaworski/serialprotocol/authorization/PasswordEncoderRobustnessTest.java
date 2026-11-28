package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class PasswordEncoderRobustnessTest {

  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private DbUserDetailsService userDetailsService;
  @Autowired
  private CustomLogoutHandler logoutHandler;

  @Test
  void anUnknownHashPrefixOrNoPrefixIsA401NotA500() throws Exception {
    for (String hash : new String[]{"{foo}x", "plainhashwithoutprefix"}) {
      repository.findByUsername("user").orElseThrow().setPassword(hash);
      entityManager.flush();

      mockMvc.perform(get("/name-service").with(httpBasic("user", "user"))).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void rolesAreTrimmed() {
    UserAccount user = repository.findByUsername("user").orElseThrow();
    user.setRoles(" ADMIN , USER");
    entityManager.flush();

    assertThat(userDetailsService.loadUserByUsername("user").getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  void logoutWithoutAnAuthenticationDoesNotFail() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatCode(() -> logoutHandler.onLogoutSuccess(new MockHttpServletRequest(), response, null))
        .doesNotThrowAnyException();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
  }
}
