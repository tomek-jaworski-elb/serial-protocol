package com.jaworski.serialprotocol.authorization;

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

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user")
                .password("user")
                .passwordEncoder(customEncoder.encoder()::encode)
                .accountExpired(false)
                .accountLocked(false)
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorizeHttpRequests) ->
                        authorizeHttpRequests
                                .requestMatchers("/name-service").hasRole("USER")
                                .requestMatchers("/api/**").hasRole("USER")
                                .requestMatchers("/admin/**").hasRole("ADMIN")
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
