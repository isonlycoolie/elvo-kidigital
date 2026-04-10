package com.elvo.identity.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.elvo.identity.monitoring.SentryExceptionReporter;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.EacManagementService;
import com.elvo.identity.service.EspManagementService;
import com.elvo.identity.service.IdentityAccountReadService;

@WebMvcTest(InternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SessionRepository sessionRepository;

    @MockBean
    private EspManagementService espManagementService;

    @MockBean
    private EacManagementService eacManagementService;

    @MockBean
    private IdentityAccountReadService accountReadService;

    @MockBean
    private SentryExceptionReporter sentryExceptionReporter;

    @Test
    void getUserStatusShouldReturnStatusAndRegisteredPhone() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        setId(user, userId);
        user.setPhone("+12025550111");
        user.setMobileVerified(true);
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        user.setVerificationStatus(User.VerificationStatus.VERIFIED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/internal/users/{userId}/status", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(userId.toString()))
            .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.data.registeredPhone").value("+12025550111"))
            .andExpect(jsonPath("$.data.mobileVerified").value(true));
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