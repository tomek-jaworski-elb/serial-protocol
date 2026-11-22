package com.jaworski.serialprotocol.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Where an account lands after login or a password change: the CRUD pages need USER, an operator gets the replay. */
@Component
@RequiredArgsConstructor
public class LandingPage {

    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority(SecurityRoles.ROLE_USER.getRole());
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority(SecurityRoles.ROLE_ADMIN.getRole());

    // Pages below USER-only prefixes; /tracks/api/** and /tracks itself are OPERATOR pages.
    private static final Pattern USER_PAGES = Pattern.compile("/(tracks/(?!api(/|$)).*|[a-z-]+-service.*|custom/.*|pdf.*|api/.*)");

    private final RoleHierarchy roleHierarchy;

    public String urlFor(Authentication auth) {
        return reaches(auth, USER) ? "/name-service" : "/tracks";
    }

    /**
     * Whether a saved request (its path, never parsed as a URI) is worth returning to.
     * shortcut: mirrors the URL rules by prefix (only /admin and the USER pages matter for a saved
     * page navigation); RoleMatrixTest fails when it disagrees with SecurityConfig for the paths it lists.
     */
    public boolean canOpen(String path, Authentication auth) {
        if (reaches(auth, ADMIN)) {
            return true;
        }
        if (path.equals("/admin") || path.startsWith("/admin/")) {
            return false;
        }
        return reaches(auth, USER) || !USER_PAGES.matcher(path).matches();
    }

    private boolean reaches(Authentication auth, SimpleGrantedAuthority role) {
        return roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities()).contains(role);
    }
}
