package com.elvo.wallet.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
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
            ObjectMapper objectMapper,
            SecretManagerService secretManagerService
    ) {
        return new InternalServiceJwtAuthenticationFilter(jwtProperties, authorizationMatrix, objectMapper, secretManagerService);
    }

    @Bean
    public UserJwtAuthenticationFilter userJwtAuthenticationFilter(UserJwtProperties jwtProperties,
                                                                   ObjectMapper objectMapper,
                                                                   UserTokenRevocationChecker tokenRevocationChecker,
                                                                   SecretManagerService secretManagerService,
                                                                   IdentityJwksKeyResolver identityJwksKeyResolver) {
        return new UserJwtAuthenticationFilter(jwtProperties, objectMapper, tokenRevocationChecker, secretManagerService, identityJwksKeyResolver);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(WalletBruteForceGuardService guardService) {
        return new WalletBruteForceAuthenticationEntryPoint(guardService);
    }

    /**
     * Security filter chain configuration for Wallet Service.
     * 
     * CSRF PROTECTION DISABLED BY DESIGN:
     * 
     * This is a stateless REST API with JWT authentication. CSRF protection is intentionally disabled because:
     * 
     * 1. All authenticated requests use Bearer token in Authorization header (not cookies)
     * 2. CSRF attacks require cookie-based state, which is not used here
     * 3. Token-in-header authentication pattern is inherently CSRF-resistant
     * 4. See UserJwtAuthenticationFilter for token extraction logic
     * 
     * CSRF vulnerabilities are prevented through:
     * - Token-based authentication (immune to CSRF)
     * - Stateless session handling (SessionCreationPolicy.STATELESS)
     * - Content-Type validation on requests
     * - CORS configuration restricting cross-origin requests
     * 
     * If future modifications introduce:
     * - Session-based authentication
     * - Cookie-based state management
     * - Form submissions
     * 
     * Then CSRF protection MUST be re-enabled via .csrf(Customizer.withDefaults())
     * 
     * See: docs/security/elvo-wallet-service-security.md#csrf-protection-posture
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            UserJwtAuthenticationFilter userJwtAuthenticationFilter,
            InternalServiceJwtAuthenticationFilter internalServiceJwtAuthenticationFilter,
            WalletBruteForceProtectionFilter bruteForceProtectionFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            MutualTlsProperties mutualTlsProperties,
            UserDetailsService mutualTlsUserDetailsService,
            Environment environment) throws Exception {
        HttpSecurity configured = http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/diagnostics/sentry-probe").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit/**").hasRole("AUDIT_ADMIN")
                    .requestMatchers("/api/v1/admin/emergency/**").hasRole("OPERATIONS_ADMIN")
                    .requestMatchers("/api/v1/admin/fraud/**").hasRole("FRAUD_ADMIN")
                    .requestMatchers("/api/v1/internal/wallets/**").hasRole("INTERNAL_SERVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(userJwtAuthenticationFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .addFilterBefore(internalServiceJwtAuthenticationFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .addFilterBefore(bruteForceProtectionFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable);

        if (mutualTlsProperties.isEnabledForProfiles(environment.getActiveProfiles())) {
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
