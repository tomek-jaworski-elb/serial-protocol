package com.jaworski.serialprotocol.entity;

import com.jaworski.serialprotocol.entity.custom.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Locale;
import java.util.UUID;

/**
 * A login of this application. Read by {@code DbUserDetailsService}, created on the first start by
 * {@code UserSeeder}. No {@code toString}: the password hash must not reach a log line.
 */
@Entity
@Table(name = UserAccount.TABLE_NAME)
@SQLRestriction("deleted_at is null")
@SQLDelete(sql = "UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP(6) WHERE app_user_uuid = ? AND version = ?")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends Auditable {

  public static final String TABLE_NAME = "app_user";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "app_user_uuid", nullable = false, updatable = false)
  private UUID uuid;

  /** Always lower case (see {@link #normalize}), so the login is case-insensitive on MariaDB and H2 alike. */
  @Column(name = "app_user_username", nullable = false, unique = true, length = 64)
  private String username;

  /** A {@code DelegatingPasswordEncoder} hash including its {@code {id}} prefix, never the raw password. */
  @Column(name = "app_user_password", nullable = false)
  private String password;

  /** Comma-separated {@code SecurityRoles} names without the {@code ROLE_} prefix, e.g. {@code ADMIN,USER}. */
  @Column(name = "app_user_roles", nullable = false, length = 100)
  private String roles;

  @Column(name = "app_user_enabled", nullable = false)
  private boolean enabled = true;

  /** Set for the seeded accounts and after an admin reset; {@code MustChangePasswordFilter} enforces it. */
  @Column(name = "app_user_must_change_password", nullable = false)
  private boolean mustChangePassword;

  public void setUsername(String username) {
    this.username = normalize(username);
  }

  /** The single place usernames are normalised, for storing and for looking up. */
  public static String normalize(String username) {
    return username == null ? null : username.toLowerCase(Locale.ROOT);
  }
}
