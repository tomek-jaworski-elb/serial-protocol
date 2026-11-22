package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** The production default of the seed flag; the suite itself runs with it switched off. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSeederTest {

  @Autowired
  private UserSeeder seeder;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private EntityManager entityManager;

  @Test
  @Transactional
  void withTheProductionDefaultTheSeededAccountsMustChangeTheirPassword() {
    entityManager.createNativeQuery("DELETE FROM app_user").executeUpdate();
    Object original = ReflectionTestUtils.getField(seeder, "mustChangePassword");
    ReflectionTestUtils.setField(seeder, "mustChangePassword", true);
    try {
      seeder.seed();
    } finally {
      ReflectionTestUtils.setField(seeder, "mustChangePassword", original);
    }

    assertThat(repository.findByUsername("user").orElseThrow().isMustChangePassword()).isTrue();
    assertThat(repository.findByUsername("admin").orElseThrow().isMustChangePassword()).isTrue();
  }
}
