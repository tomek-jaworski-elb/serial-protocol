package com.jaworski.serialprotocol.service.db;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rolls back every test, so {@code app_user} keeps only the seeded accounts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class UserAccountServiceTest {

  private static final String PASSWORD = "correct horse battery";

  @Autowired
  private UserAccountService service;
  @Autowired
  private UserAccountRepository repository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private EntityManager entityManager;

  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void captureLog() {
    logger = (Logger) LoggerFactory.getLogger(UserAccountService.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(appender);
  }

  @Test
  void createStoresAHashedFlaggedLowerCaseAccount() {
    service.create("admin", "Bob", PASSWORD, false);

    UserAccount bob = repository.findByUsername("bob").orElseThrow();
    assertThat(bob.getPassword()).startsWith("{bcrypt}");
    assertThat(passwordEncoder.matches(PASSWORD, bob.getPassword())).isTrue();
    assertThat(bob.isMustChangePassword()).isTrue();
    assertThat(bob.getRoles()).isEqualTo("USER");
  }

  @Test
  void anAdministratorIsStoredWithTheUserRole() {
    service.create("admin", "boss", PASSWORD, true);
    assertThat(repository.findByUsername("boss").orElseThrow().getRoles()).isEqualTo("ADMIN,USER");

    UserAccount bob = createBob();
    service.setAdmin("admin", bob.getUuid(), null, true);
    assertThat(repository.findById(bob.getUuid()).orElseThrow().getRoles()).isEqualTo("ADMIN,USER");
    service.setAdmin("admin", bob.getUuid(), null, false);
    assertThat(repository.findById(bob.getUuid()).orElseThrow().getRoles()).isEqualTo("USER");
  }

  @Test
  void aDuplicateIsRejectedEvenInOtherCaseOrAsATombstone() {
    service.create("admin", "bob", PASSWORD, false);

    assertThatThrownBy(() -> service.create("admin", "BOB", PASSWORD, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");

    repository.delete(repository.findByUsername("bob").orElseThrow());
    entityManager.flush();
    assertThatThrownBy(() -> service.create("admin", "bob", PASSWORD, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
  }

  @Test
  void badUsernamesAreRejected() {
    for (String bad : new String[]{"", "   ", "a".repeat(65), "bo\nb", "bo\u0000b"}) {
      assertThatThrownBy(() -> service.create("admin", bad, PASSWORD, false))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatCode(() -> service.create("admin", "a@b.c", PASSWORD, false)).doesNotThrowAnyException();
  }

  @Test
  void thePasswordPolicyCountsCodePointsAndBytes() {
    assertThatThrownBy(() -> service.create("admin", "p1", "a".repeat(11), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> service.create("admin", "p2", "a".repeat(12), false)).doesNotThrowAnyException();
    String emoji = "😀";
    assertThatCode(() -> service.create("admin", "p3", emoji.repeat(12), false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> service.create("admin", "p4", emoji.repeat(19), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> service.create("admin", "p5", "a".repeat(72), false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> service.create("admin", "p6", "a".repeat(73), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.create("admin", "p7", null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void thePasswordIsNotTrimmed() {
    String padded = "  " + PASSWORD + "  ";
    service.create("admin", "bob", padded, false);

    String hash = repository.findByUsername("bob").orElseThrow().getPassword();
    assertThat(passwordEncoder.matches(padded, hash)).isTrue();
    assertThat(passwordEncoder.matches(PASSWORD, hash)).isFalse();
  }

  @Test
  void anAdminCannotDisableOrDemoteThemselves() {
    UUID admin = repository.findByUsername("admin").orElseThrow().getUuid();

    assertThatThrownBy(() -> service.setEnabled("admin", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("own account");
    assertThatThrownBy(() -> service.setAdmin("admin", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("own account");
  }

  @Test
  void aSelfResetSucceedsAndSetsTheFlag() {
    UserAccount admin = repository.findByUsername("admin").orElseThrow();
    admin.setMustChangePassword(false);

    service.resetPassword("admin", admin.getUuid(), null, PASSWORD);

    assertThat(repository.findByUsername("admin").orElseThrow().isMustChangePassword()).isTrue();
  }

  @Test
  void theLastEnabledAdministratorStays() {
    UUID admin = repository.findByUsername("admin").orElseThrow().getUuid();

    assertThatThrownBy(() -> service.setAdmin("someone", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("At least one enabled administrator");
    assertThatThrownBy(() -> service.setEnabled("someone", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("At least one enabled administrator");
  }

  @Test
  void withASecondAdministratorTheFirstCanGo() {
    service.create("admin", "boss", PASSWORD, true);
    entityManager.flush();
    UUID admin = repository.findByUsername("admin").orElseThrow().getUuid();

    assertThat(service.setEnabled("boss", admin, null, false)).isEqualTo("admin");
    assertThat(repository.findById(admin).orElseThrow().isEnabled()).isFalse();
  }

  @Test
  void aUsernameWithLeadingOrTrailingWhitespaceIsRejected() {
    for (String bad : new String[]{" bob", "bob ", "\tbob"}) {
      assertThatThrownBy(() -> service.create("admin", bad, PASSWORD, false))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start or end with a space");
    }
  }

  @Test
  void aDisabledSecondAdministratorDoesNotCountAsOneRemaining() {
    UserAccount boss = createAdminBoss();
    UUID admin = repository.findByUsername("admin").orElseThrow().getUuid();
    service.setEnabled("admin", boss.getUuid(), null, false);

    assertThatThrownBy(() -> service.setAdmin("someone", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("At least one enabled administrator");
    assertThatThrownBy(() -> service.setEnabled("someone", admin, null, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("At least one enabled administrator");
  }

  @Test
  void anOwnChangeWithABadNewOrMissingCurrentPasswordChangesNothing() {
    UserAccount bob = createBob();
    String hash = bob.getPassword();

    assertThatThrownBy(() -> service.changeOwnPassword("bob", PASSWORD, "short"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least");
    assertThatThrownBy(() -> service.changeOwnPassword("bob", null, "another password!"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Current password is incorrect");

    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findById(bob.getUuid()).orElseThrow().getPassword()).isEqualTo(hash);
  }

  @Test
  void aNoOpChangeReportsNothingChanged() {
    UserAccount bob = createBob();

    assertThat(service.setAdmin("admin", bob.getUuid(), null, false)).isNull();
    assertThat(service.setEnabled("admin", bob.getUuid(), null, true)).isNull();
  }

  @Test
  void aStaleVersionIsRefused() {
    UserAccount bob = createBob();

    assertThatThrownBy(() -> service.setEnabled("admin", bob.getUuid(), bob.getVersion() + 5, false))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThatThrownBy(() -> service.resetPassword("admin", bob.getUuid(), bob.getVersion() + 5, PASSWORD))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void anOwnChangeNeedsTheCurrentPasswordAndANewOne() {
    UserAccount bob = createBob();

    assertThatThrownBy(() -> service.changeOwnPassword("bob", "wrong wrong wrong", "another password!"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Current password is incorrect");
    assertThatThrownBy(() -> service.changeOwnPassword("bob", PASSWORD, PASSWORD))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("differ");

    service.changeOwnPassword("bob", PASSWORD, "another password!");

    UserAccount changed = repository.findById(bob.getUuid()).orElseThrow();
    assertThat(changed.isMustChangePassword()).isFalse();
    assertThat(passwordEncoder.matches("another password!", changed.getPassword())).isTrue();
  }

  @Test
  void theAuditLogNamesTheActionButNeverThePasswordOrHash() {
    UserAccount bob = createBob();
    service.resetPassword("admin", bob.getUuid(), null, "reset secret phrase");
    service.setEnabled("admin", bob.getUuid(), null, false);
    service.changeOwnPassword("bob", "reset secret phrase", "own secret phrase!");
    entityManager.flush();

    List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(lines).anyMatch(l -> l.contains("Admin 'admin' created user 'bob'"));
    assertThat(lines).anyMatch(l -> l.contains("Admin 'admin' reset the password of 'bob'"));
    assertThat(lines).anyMatch(l -> l.contains("Admin 'admin' disabled 'bob'"));
    assertThat(lines).anyMatch(l -> l.contains("User 'bob' changed own password"));
    String hash = repository.findByUsername("bob").orElseThrow().getPassword();
    assertThat(String.join("\n", lines)).doesNotContain("secret phrase").doesNotContain(PASSWORD)
        .doesNotContain(hash).doesNotContain("{bcrypt}");
  }

  private UserAccount createAdminBoss() {
    service.create("admin", "boss", PASSWORD, true);
    entityManager.flush();
    return repository.findByUsername("boss").orElseThrow();
  }

  private UserAccount createBob() {
    service.create("admin", "bob", PASSWORD, false);
    entityManager.flush();
    return repository.findByUsername("bob").orElseThrow();
  }
}
