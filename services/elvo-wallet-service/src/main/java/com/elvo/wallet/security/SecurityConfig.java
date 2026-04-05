package com.elvo.wallet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public WalletBruteForceGuardService walletBruteForceGuardService() {
        return new WalletBruteForceGuardService();
    }

    @Bean
    public WalletBruteForceProtectionFilter walletBruteForceProtectionFilter(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceProtectionFilter(guardService);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceAuthenticationEntryPoint(guardService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            WalletBruteForceProtectionFilter bruteForceProtectionFilter,
            AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(bruteForceProtectionFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
