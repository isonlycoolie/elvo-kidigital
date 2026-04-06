package com.elvo.wallet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConfigurationProperties(prefix = "elvo.security.internal-jwt")
    public InternalServiceJwtProperties internalServiceJwtProperties() {
        return new InternalServiceJwtProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "elvo.security.internal-authz")
    public InternalServiceAuthorizationProperties internalServiceAuthorizationProperties() {
        return new InternalServiceAuthorizationProperties();
    }

    @Bean
    public WalletBruteForceGuardService walletBruteForceGuardService() {
        return new WalletBruteForceGuardService();
    }

    @Bean
    public WalletBruteForceProtectionFilter walletBruteForceProtectionFilter(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceProtectionFilter(guardService);
    }

    @Bean
    public InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter(
            InternalServiceJwtProperties jwtProperties,
            InternalServiceAuthorizationMatrix authorizationMatrix,
            ObjectMapper objectMapper
    ) {
        return new InternalServiceJwtAuthenticationFilter(jwtProperties, authorizationMatrix, objectMapper);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceAuthenticationEntryPoint(guardService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter,
            WalletBruteForceProtectionFilter bruteForceProtectionFilter,
            AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/api/v1/internal/wallets/**").hasRole("INTERNAL_SERVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(internalServiceJwtAuthenticationFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .addFilterBefore(bruteForceProtectionFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
