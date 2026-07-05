package com.elvo.accountmanagement.security;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(InternalServiceJwtProperties.class)
public class SecurityConfig {

    @Bean
    public InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter(
            InternalServiceJwtProperties jwtProperties,
            ObjectMapper objectMapper) {
        return new InternalServiceJwtAuthenticationFilter(jwtProperties, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/diagnostics/sentry-probe").permitAll()
                        .requestMatchers("/api/v1/internal/accounts/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(internalServiceJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
