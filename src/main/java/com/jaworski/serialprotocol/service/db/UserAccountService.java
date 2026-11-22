package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.authorization.SecurityRoles;
import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import com.jaworski.serialprotocol.service.db.custom.OptimisticLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every change to a login's password, roles or enabled flag goes through here, so the password policy
 * and the administrator guards cannot be skipped. Nothing here logs or returns a password or a hash.
 * {@code UserSeeder} is the one deliberate bypass of the password policy: it stores the configured seed
 * credentials as given on the first start.
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

  private static final Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

  private static final int MIN_PASSWORD_CODE_POINTS = 12;
  /** bcrypt ignores everything past 72 bytes, so a longer password would be silently truncated. */
  private static final int MAX_PASSWORD_BYTES = 72;
  private static final int MAX_USERNAME_LENGTH = 64;
  private static final String LAST_ADMIN = "At least one enabled administrator must remain";
  private static final String USER_NOT_FOUND = "User not found";
  private static final String ENTITY = "User";
  private static final String OWN_ACCOUNT = "You cannot disable or demote your own account";

  private final UserAccountRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRegistry sessionRegistry;

  public record UserRow(UUID id, String username, boolean admin, boolean enabled, boolean mustChangePassword,
                        long version) {
  }

  @Transactional(readOnly = true)
  public List<UserRow> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(UserAccount::getUsername))
        .map(u -> new UserRow(u.getUuid(), u.getUsername(), isAdmin(u), u.isEnabled(), u.isMustChangePassword(),
            u.getVersion()))
        .toList();
  }

  @Transactional
  public void create(String actor, String username, String password, boolean admin) {
    String name = UserAccount.normalize(username);
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Username must not be blank");
    }
    if (name.strip().length() != name.length()) {
      throw new IllegalArgumentException("Username must not start or end with a space");
    }
    if (name.length() > MAX_USERNAME_LENGTH) {
      throw new IllegalArgumentException("Username must be at most " + MAX_USERNAME_LENGTH + " characters");
    }
    // Keeps control characters out of the log lines below.
    if (name.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Username must not contain control characters");
    }
    checkPassword(password);
    String taken = "User '" + name + "' already exists.";
    if (repository.countWithUsername(name) > 0) {
      throw new IllegalArgumentException(taken);
    }
    UserAccount account = new UserAccount();
    account.setUsername(name);
    account.setPassword(passwordEncoder.encode(password));
    account.setRoles(canonicalRoles(admin));
    account.setMustChangePassword(true);
    try {
      repository.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException(taken, e);
    }
    LOG.info("Admin '{}' created user '{}' (admin={})", actor, name, admin);
  }

  /** @return the target's username when its sessions must end (the role changed), else null */
  @Transactional
  public String setAdmin(String actor, UUID id, Long version, boolean admin) {
    List<UserAccount> admins = lockedAdmins();
    UserAccount target = load(id);
    OptimisticLock.check(version, target.getVersion(), ENTITY);
    if (isAdmin(target) == admin) {
      return null;
    }
    if (!admin) {
      guardRemoval(actor, target, admins);
    }
    target.setRoles(canonicalRoles(admin));
    repository.saveAndFlush(target);
    LOG.info("Admin '{}' set admin={} for '{}'", actor, admin, target.getUsername());
    return target.getUsername();
  }

  /** @return the target's username when a disable changed something and its sessions must end, else null */
  @Transactional
  public String setEnabled(String actor, UUID id, Long version, boolean enabled) {
    List<UserAccount> admins = lockedAdmins();
    UserAccount target = load(id);
    OptimisticLock.check(version, target.getVersion(), ENTITY);
    if (target.isEnabled() == enabled) {
      return null;
    }
    if (!enabled) {
      guardRemoval(actor, target, admins);
    }
    target.setEnabled(enabled);
    repository.saveAndFlush(target);
    LOG.info("Admin '{}' {} '{}'", actor, enabled ? "enabled" : "disabled", target.getUsername());
    // Enabling ends no session.
    return enabled ? null : target.getUsername();
  }

  /** @return the target's username, whose sessions must end */
  @Transactional
  public String resetPassword(String actor, UUID id, Long version, String password) {
    UserAccount target = load(id);
    OptimisticLock.check(version, target.getVersion(), ENTITY);
    checkPassword(password);
    target.setPassword(passwordEncoder.encode(password));
    target.setMustChangePassword(true);
    repository.saveAndFlush(target);
    LOG.info("Admin '{}' reset the password of '{}'", actor, target.getUsername());
    return target.getUsername();
  }

  @Transactional
  public void changeOwnPassword(String username, String current, String next) {
    UserAccount account = repository.findByUsername(UserAccount.normalize(username))
        .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
    if (current == null || !passwordEncoder.matches(current, account.getPassword())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }
    checkPassword(next);
    if (passwordEncoder.matches(next, account.getPassword())) {
      throw new IllegalArgumentException("The new password must differ from the current one");
    }
    account.setPassword(passwordEncoder.encode(next));
    account.setMustChangePassword(false);
    repository.saveAndFlush(account);
    LOG.info("User '{}' changed own password", account.getUsername());
  }

  /**
   * Ends the user's HTTP sessions except the given ids; the next request of each is redirected to
   * {@code /login?expired}. Not transactional: callers invoke it after the change has committed.
   */
  public void expireSessions(String username, String... keepSessionIds) {
    Set<String> keep = new HashSet<>(Arrays.asList(keepSessionIds));
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      if (principal instanceof UserDetails u && u.getUsername().equals(username)) {
        for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
          if (!keep.contains(session.getSessionId())) {
            session.expireNow();
          }
        }
      }
    }
  }

  private void guardRemoval(String actor, UserAccount target, List<UserAccount> enabledAdmins) {
    if (target.getUsername().equals(UserAccount.normalize(actor))) {
      LOG.warn("Refused: admin '{}' tried to disable or demote own account", actor);
      throw new IllegalArgumentException(OWN_ACCOUNT);
    }
    boolean targetIsEnabledAdmin = enabledAdmins.stream().anyMatch(a -> a.getUuid().equals(target.getUuid()));
    if (targetIsEnabledAdmin && enabledAdmins.size() < 2) {
      LOG.warn("Refused: '{}' would leave no enabled administrator", target.getUsername());
      throw new IllegalArgumentException(LAST_ADMIN);
    }
  }

  private UserAccount load(UUID id) {
    return repository.findById(id).orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
  }

  /** The LIKE query is only the lock set: a role that merely contains the text ADMIN is not an administrator. */
  private List<UserAccount> lockedAdmins() {
    return repository.lockEnabledAdmins().stream().filter(UserAccountService::isAdmin).toList();
  }

  private static boolean isAdmin(UserAccount account) {
    return Arrays.asList(StringUtils.tokenizeToStringArray(account.getRoles(), ","))
        .contains(SecurityRoles.ROLE_ADMIN.getName());
  }

  /** ADMIN is always stored with USER: there is no role hierarchy. */
  private static String canonicalRoles(boolean admin) {
    String user = SecurityRoles.ROLE_USER.getName();
    return admin ? SecurityRoles.ROLE_ADMIN.getName() + "," + user : user;
  }

  // Not trimmed or normalised: the login form does not do it either, so the two must agree.
  private static void checkPassword(String raw) {
    if (raw == null || raw.codePointCount(0, raw.length()) < MIN_PASSWORD_CODE_POINTS) {
      throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_CODE_POINTS + " characters");
    }
    if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new IllegalArgumentException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes in UTF-8");
    }
  }
}
