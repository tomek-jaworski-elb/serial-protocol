package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  /** Expects a {@link UserAccount#normalize normalised} name; soft-deleted accounts are not found. */
  Optional<UserAccount> findByUsername(String username);

  /** Native so that soft-deleted accounts count too: the seed runs only on a table that never had a row. */
  @Query(value = "SELECT COUNT(*) FROM app_user", nativeQuery = true)
  long countIncludingDeleted();
}
