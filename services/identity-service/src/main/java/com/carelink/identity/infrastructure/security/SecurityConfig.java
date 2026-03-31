package com.carelink.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public final class SecurityConfig {

    /**
     * Configures stateless JWT-based security.
     *
     * @param http spring security builder
     * @param jwtService jwt parser and validator
     * @return security filter chain
     * @throws Exception when security cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final JwtService jwtService) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(
            sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated()
        );
        http.addFilterBefore(
            new JwtAuthenticationFilter(jwtService),
            UsernamePasswordAuthenticationFilter.class
        );
        return http.build();
    }
}
