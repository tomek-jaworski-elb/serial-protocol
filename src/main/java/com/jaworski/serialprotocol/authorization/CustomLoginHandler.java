package com.jaworski.serialprotocol.authorization;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Sends the user back to the protected page that bounced them to the login form, else to {@code /name-service}. */
@Component
public class CustomLoginHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CustomLoginHandler.class);

    public CustomLoginHandler() {
        setDefaultTargetUrl("/name-service");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        LOG.info("User {} logged in successfully", authentication.getName());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
