package com.elvo.wallet.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    @ConfigurationProperties(prefix = "elvo.security.jwt")
    public UserJwtProperties userJwtProperties() {
        return new UserJwtProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "elvo.security.internal-authz")
    public InternalServiceAuthorizationProperties internalServiceAuthorizationProperties() {
        return new InternalServiceAuthorizationProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "elvo.security.mtls")
    public MutualTlsProperties mutualTlsProperties() {
        return new MutualTlsProperties();
    }

    @Bean
    public UserDetailsService mutualTlsUserDetailsService(MutualTlsProperties mutualTlsProperties) {
        return username -> {
            if (mutualTlsProperties.getTrustedCommonNames() == null
                    || mutualTlsProperties.getTrustedCommonNames().stream().noneMatch(username::equalsIgnoreCase)) {
                throw new UsernameNotFoundException("Untrusted mTLS client certificate subject");
            }
            UserDetails principal = User.withUsername(username)
                    .password("N/A")
                    .roles("INTERNAL_SERVICE")
                    .build();
            return principal;
        };
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
    public UserJwtAuthenticationFilter userJwtAuthenticationFilter(UserJwtProperties jwtProperties, ObjectMapper objectMapper) {
        return new UserJwtAuthenticationFilter(jwtProperties, objectMapper);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceAuthenticationEntryPoint(guardService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            UserJwtAuthenticationFilter userJwtAuthenticationFilter,
            InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter,
            WalletBruteForceProtectionFilter bruteForceProtectionFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            MutualTlsProperties mutualTlsProperties,
            UserDetailsService mutualTlsUserDetailsService) throws Exception {
        HttpSecurity configured = http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit/**").hasAnyRole("ADMIN", "AUDIT_ADMIN")
                    .requestMatchers("/api/v1/internal/wallets/**").hasRole("INTERNAL_SERVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(userJwtAuthenticationFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .addFilterBefore(internalServiceJwtAuthenticationFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .addFilterBefore(bruteForceProtectionFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        if (mutualTlsProperties.isEnabled()) {
            configured.requiresChannel(channel -> channel
                    .requestMatchers("/api/v1/internal/**")
                    .requiresSecure());
            configured.x509(x509 -> x509
                    .subjectPrincipalRegex(mutualTlsProperties.getSubjectPrincipalRegex())
                    .userDetailsService(mutualTlsUserDetailsService));
        }

        return configured.build();
    }
}
