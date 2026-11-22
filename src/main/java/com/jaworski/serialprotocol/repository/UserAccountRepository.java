package com.jaworski.serialprotocol.repository;

import com.jaworski.serialprotocol.entity.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  /** Expects a {@link UserAccount#normalize normalised} name; soft-deleted accounts are not found. */
  Optional<UserAccount> findByUsername(String username);

  /** Native so that soft-deleted accounts count too: the seed runs only on a table that never had a row. */
  @Query(value = "SELECT COUNT(*) FROM app_user", nativeQuery = true)
  long countIncludingDeleted();

  /** Locks the enabled administrators (SELECT ... FOR UPDATE); soft-deleted rows are not returned. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.enabled = true and u.roles like '%ADMIN%'")
  List<UserAccount> lockEnabledAdmins();

  /** Native so that tombstones keep a name taken. */
  @Query(value = "SELECT COUNT(*) FROM app_user WHERE LOWER(app_user_username) = LOWER(:username)", nativeQuery = true)
  long countWithUsername(@Param("username") String username);
}
