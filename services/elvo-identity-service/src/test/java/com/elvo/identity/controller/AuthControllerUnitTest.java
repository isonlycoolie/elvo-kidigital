package com.elvo.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.contract.RiskDecisionContract;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.monitoring.SentryExceptionReporter;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.security.TokenRevocationService;
import com.elvo.identity.service.LoginService;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.service.OtpService;
import com.elvo.identity.service.PostVerificationProvisioningService;
import com.elvo.identity.service.RegistrationService;
import com.elvo.identity.service.RiskScoringService;
import com.elvo.identity.service.VerificationTokenService;
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
    private OtpService otpService;

    @MockBean
    private VerificationTokenService verificationTokenService;

    @MockBean
    private PostVerificationProvisioningService postVerificationProvisioningService;

    @MockBean
    private IdentityAccountReadService accountReadService;

    @MockBean
    private RiskScoringService riskScoringService;

    @MockBean
    private SentryExceptionReporter sentryExceptionReporter;

    @BeforeEach
    void setupRiskDefaults() {
      when(riskScoringService.evaluateOnboarding(any(Boolean.class), any(), any()))
        .thenReturn(RiskDecisionContract.allow(10, java.util.List.of()));
    }

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
      UUID userId = UUID.randomUUID();
      User user = new User();
      setId(user, userId);
      user.setEmail("user@elvo.com");
      user.setPhone("+12025550111");

        when(registrationService.register(any())).thenReturn(
        new RegistrationResponse(userId, "ELVO-UNIT-123456", "user@elvo.com", "+12025550111", true));
      when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
      when(otpService.issueVerificationOtp(any(), any(), any(), any(), eq(false), any(), any(), any(), any()))
        .thenReturn(new OtpService.OtpDispatchResult("req-1", "u***@elvo.com", java.time.Instant.now().plusSeconds(300)));
      when(verificationTokenService.issueToken(userId))
        .thenReturn(new VerificationTokenService.VerificationToken("verification-token", java.time.Instant.now().plusSeconds(600)));

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
                void verifyEmailOtpShouldNotProvisionBeforeFullVerification() throws Exception {
              UUID userId = UUID.randomUUID();
              User user = new User();
              setId(user, userId);
              user.setEmail("user@elvo.com");
              user.setPhone("+12025550111");
              user.setEmailVerified(false);
              user.setMobileVerified(false);
              user.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);

              when(userRepository.findByEmailIgnoreCase("user@elvo.com")).thenReturn(java.util.Optional.of(user));
              when(verificationTokenService.isValidForUser("verification-token", userId)).thenReturn(true);
              when(otpService.verifyOtp(eq(user), eq(VerificationOtp.Purpose.EMAIL_VERIFICATION), eq("123456"), any(), any(), any()))
                .thenReturn(new OtpService.OtpVerificationResult(true, "OTP_VERIFIED", "Verification successful"));

              mockMvc.perform(post("/auth/verify-email-otp")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("""
                    {
                      "identifier": "user@elvo.com",
                      "otpCode": "123456",
                      "verificationToken": "verification-token"
                    }
                    """))
                .andExpect(status().isOk());

              verify(postVerificationProvisioningService, never()).provisionIfNeeded(any());
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

                private void setId(Object target, UUID id) {
                  try {
                    java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(target, id);
                  } catch (NoSuchFieldException | IllegalAccessException ex) {
                    throw new IllegalStateException("Unable to set test id", ex);
                  }
                }
}
