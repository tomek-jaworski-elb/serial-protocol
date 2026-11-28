package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The only {@link UserDetailsService} bean: Spring Security builds its {@code DaoAuthenticationProvider}
 * from it and from the {@code PasswordEncoder} bean only while each of them is unique.
 */
@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

  private final UserAccountRepository repository;

  @Override
  public UserDetails loadUserByUsername(String username) {
    UserAccount account = repository.findByUsername(UserAccount.normalize(username))
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    // The stored value is already a hash: no passwordEncoder(...) on the builder, or every login fails.
    return User.withUsername(account.getUsername())
        .password(account.getPassword())
        .disabled(!account.isEnabled())
        .roles(StringUtils.tokenizeToStringArray(account.getRoles(), ","))
        .build();
  }
}
