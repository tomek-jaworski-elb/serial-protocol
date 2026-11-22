package com.jaworski.serialprotocol.authorization;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends the user back to the protected page that bounced them to the login form when they may open it,
 * else to their {@link LandingPage}.
 */
@Component
@RequiredArgsConstructor
public class CustomLoginHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CustomLoginHandler.class);

    private final LandingPage landingPage;
    // Same session attribute as the cache SecurityConfig installs: only its matcher differs.
    private final RequestCache cache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        LOG.info("User {} logged in successfully", authentication.getName());
        SavedRequest saved = cache.getRequest(request, response);
        // The path only: the query may hold escapes a URI parser rejects. The cache stores nothing else than DefaultSavedRequest.
        if (saved != null && !(saved instanceof DefaultSavedRequest d && landingPage.canOpen(d.getRequestURI(), authentication))) {
            cache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        return landingPage.urlFor(authentication);
    }
}
