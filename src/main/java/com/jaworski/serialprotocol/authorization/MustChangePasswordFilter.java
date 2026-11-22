package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.entity.UserAccount;
import com.jaworski.serialprotocol.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Keeps an account whose {@code must_change_password} flag is set on the change-password page. Wired in
 * {@code SecurityConfig} only: as a {@code @Component} Boot would also register it as a plain servlet
 * filter, where it would run before authentication. The flag is read from the database on every request
 * (not from the session principal, which is a stale snapshot), so it also covers HTTP Basic and needs no
 * refresh after a change.
 */
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

  static final String CHANGE_PAGE = "/account/password";

  private static final RequestMatcher ALLOWED = new OrRequestMatcher(Stream.of(
          CHANGE_PAGE, "/login", "/logout", "/error", "/.well-known/change-password",
          "/css/**", "/js/**", "/fonts/**", "/img/**", "/bootstrap-5-3-7/**", "/bootstrap-5-3-8/**",
          "/flatpickr/**", "/nouislider/**", "/favicon.ico",
          "/rs", "/json", "/heartbeat", "/session")
      .map(p -> (RequestMatcher) PathPatternRequestMatcher.pathPattern(p))
      .toList());

  private static final RequestMatcher PAGE_NAVIGATION = SecurityConfig.pageNavigation();

  private final UserAccountRepository repository;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
        && !ALLOWED.matches(request) && mustChange(auth.getName())) {
      if (PAGE_NAVIGATION.matches(request)) {
        response.sendRedirect(request.getContextPath() + CHANGE_PAGE);
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required");
      }
      return;
    }
    chain.doFilter(request, response);
  }

  // shortcut: one indexed DB read per authenticated non-static request, add a short-lived cache if load ever matters
  private boolean mustChange(String username) {
    return repository.findByUsername(UserAccount.normalize(username))
        .map(UserAccount::isMustChangePassword).orElse(false);
  }
}
