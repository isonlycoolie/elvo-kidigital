package com.elvo.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.monitoring.SentryExceptionReporter;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.security.TokenRevocationService;
import com.elvo.identity.service.LoginService;
import com.elvo.identity.service.RegistrationService;
import com.elvo.identity.util.TokenService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private LoginService loginService;

    @MockBean
    private SessionRepository sessionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AuditRepository auditRepository;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private SecurityHashingService hashingService;

    @MockBean
    private TokenRevocationService tokenRevocationService;

    @MockBean
    private SentryExceptionReporter sentryExceptionReporter;

    @Test
    void registerShouldReturnBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "phone": "abc",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerShouldReturnOkForValidPayload() throws Exception {
        when(registrationService.register(any())).thenReturn(
                new RegistrationResponse(UUID.randomUUID(), "ELVO-UNIT-123456", "user@elvo.com", "+12025550111", true));

        mockMvc.perform(post("/auth/register")
            .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@elvo.com",
                                  "phone": "+12025550111",
                                  "password": "Password123",
                                  "enableMfa": true,
                                  "sourceIp": "127.0.0.1",
                                  "sourceUserAgent": "JUnit"
                                }
                                """))
                .andExpect(status().isOk());
    }

                            @Test
                            void refreshTokenFailureShouldEmitStructuredAuthFailureAudit() throws Exception {
                          when(tokenService.validateRefreshToken(any())).thenThrow(new IllegalArgumentException("Token is invalid"));

                          mockMvc.perform(post("/auth/refresh-token")
                              .with(csrf())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content("""
                                {
                                  "refreshToken": "invalid.token.value",
                                  "sourceIp": "127.0.0.1",
                                  "sourceUserAgent": "JUnit"
                                }
                                """))
                            .andExpect(status().is5xxServerError());

                          verify(auditRepository).save(argThat(audit ->
                            audit.getDescription() != null
                              && audit.getDescription().startsWith("AUTH_FAILURE|flow=refresh-token|reason=TOKEN_INVALID")));
                            }
}
