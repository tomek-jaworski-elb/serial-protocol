package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomEncoder customEncoder;
    private final CustomLogoutHandler customLogoutHandler;
    private final CustomLoginHandler customLoginHandler;
    private final Resources resources;

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername(resources.getServerUser())
                .password(resources.getServerPassword())
                .passwordEncoder(customEncoder.encoder()::encode)
                .accountExpired(false)
                .accountLocked(false)
                .roles(SecurityRoles.ROLE_USER.getName())
                .build();

      UserDetails userAdmin = User.withUsername(resources.getServerAdminUser())
              .password(resources.getServerAdminPassword())
              .passwordEncoder(customEncoder.encoder()::encode)
              .accountExpired(false)
              .accountLocked(false)
              .roles(SecurityRoles.ROLE_ADMIN.getName(), SecurityRoles.ROLE_USER.getName())
              .build();

        return new InMemoryUserDetailsManager(userDetails, userAdmin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorizeHttpRequests) ->
                        authorizeHttpRequests
                                .requestMatchers("/name-service").hasRole(SecurityRoles.ROLE_USER.getName())
                                .requestMatchers("/instructor-service").hasRole(SecurityRoles.ROLE_USER.getName())
                                .requestMatchers("/api/**").hasRole(SecurityRoles.ROLE_USER.getName())
                                .requestMatchers("/admin/**").hasRole(SecurityRoles.ROLE_ADMIN.getName())
                                .anyRequest().permitAll()
                )
                .httpBasic(withDefaults())
                .formLogin((formLogin) ->
                                formLogin
                                        .usernameParameter("username")
                                        .passwordParameter("password")
                                        .permitAll()
                                        .loginPage("/login")
                                        .failureUrl("/login?error")
                                        .loginProcessingUrl("/login")
                                        .successHandler(customLoginHandler)

                )
                .logout((logout) ->
                                logout.deleteCookies("JSESSIONID")
                                        .invalidateHttpSession(true)
                                        .permitAll()
                                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                                        .logoutSuccessHandler(customLogoutHandler)
                )
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
