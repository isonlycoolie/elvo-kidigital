package com.elvo.accountmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
class InternalPingIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void prometheusExposureAndSecurityChainShouldBeConfigured() {
        String exposure = environment.getProperty("management.endpoints.web.exposure.include");
        assertThat(exposure).contains("prometheus");
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isNotEmpty();
    }
}