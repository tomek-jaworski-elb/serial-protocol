package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the initial user and admin from {@code custom.server.credentials.*} when {@code app_user}
 * has never held a row. After that the properties change nothing: passwords live in the database.
 */
@Component
@RequiredArgsConstructor
public class UserSeeder {

  private static final Logger LOG = LoggerFactory.getLogger(UserSeeder.class);

  private final UserAccountRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final Resources resources;

  // Not final: UserSeederTest flips it to prove the production default.
  @Value("${custom.server.credentials.must-change-password:true}")
  private boolean mustChangePassword;

  // shortcut: two instances starting on an empty table at once both insert, and the loser fails
  // its start on the unique username; restart it. Upgrade if the app ever runs as several instances.
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seed() {
    if (repository.countIncludingDeleted() > 0) {
      return;
    }
    String userRole = SecurityRoles.ROLE_USER.getName();
    String adminRole = SecurityRoles.ROLE_ADMIN.getName();
    String userName = resources.getServerUser();
    String adminName = resources.getServerAdminUser();
    repository.saveAll(List.of(
        account(userName, resources.getServerPassword(), userRole),
        account(adminName, resources.getServerAdminPassword(), adminRole)));
    LOG.warn("Created the initial accounts '{}' and '{}'; change their passwords", userName, adminName);
  }

  private UserAccount account(String username, String rawPassword, String roles) {
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setPassword(passwordEncoder.encode(rawPassword));
    account.setRoles(roles);
    account.setMustChangePassword(mustChangePassword);
    return account;
  }
}
