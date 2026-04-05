package com.elvo.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.elvo.identity.entity.User;
import com.elvo.identity.entity.Device;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SecurityHashingService hashingService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void registrationShouldPersistUserAndDispatchAuditEvent() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "integration.user@elvo.com",
                                  "phone": "+12025550222",
                                  "password": "Password123",
                                  "enableMfa": true,
                                  "sourceIp": "127.0.0.1",
                                  "sourceUserAgent": "Integration-Test"
                                }
                                """))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByEmailIgnoreCase("integration.user@elvo.com").isPresent());
        verify(rabbitTemplate, timeout(3000).atLeastOnce())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void loginShouldCreateSessionRecord() throws Exception {
        User user = new User();
        user.setEan("ELVO-LOGIN-00001");
        user.setEmail("login.user@elvo.com");
        user.setPhone("+12025550333");
        user.setHashedPassword(hashingService.hashPassword("StrongPass123"));
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        user.setMfaEnabled(false);
        user.setEspEnabled(false);
        userRepository.save(user);

        Device device = new Device();
        device.setUser(user);
        device.setDeviceId("device-integration-001");
        device.setDeviceType("ANDROID");
        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(false);
        device.setLastUsedAt(Instant.now());
        deviceRepository.save(device);

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "identifier": "login.user@elvo.com",
                                  "password": "StrongPass123",
                                  "deviceId": "device-integration-001",
                                  "deviceType": "ANDROID",
                                  "sourceIp": "127.0.0.1",
                                  "sourceUserAgent": "Integration-Test"
                                }
                                """))
                .andExpect(status().isOk());

        assertTrue(sessionRepository.findByUserIdAndActiveTrueAndRevokedFalseOrderByCreatedAtDesc(user.getId()).size() > 0);
    }

    @Test
    void protectedEndpointShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
