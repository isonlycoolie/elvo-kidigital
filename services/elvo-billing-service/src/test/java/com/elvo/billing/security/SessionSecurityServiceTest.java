package com.elvo.billing.security;

import com.elvo.billing.entity.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSecurityServiceTest {

    private SessionSecurityService sessionSecurityService;

    @BeforeEach
    void setUp() throws Exception {
        sessionSecurityService = new SessionSecurityService();
        setField("accessTokenDurationSeconds", 60L);
        setField("refreshTokenDurationSeconds", 120L);
        setField("maxConcurrentSessions", 2);
    }

    @Test
    void createSessionGeneratesValidTokens() {
        UserSession session = sessionSecurityService.createSession(
                UUID.randomUUID(),
                "device-1",
                "fingerprint-1",
                "JUnit",
                "127.0.0.1");

        assertTrue(session.getAccessToken() != null && !session.getAccessToken().isBlank());
        assertTrue(session.getRefreshToken() != null && !session.getRefreshToken().isBlank());
        assertTrue(sessionSecurityService.getUserActiveSessions(session.getUserId()).contains(session));
    }

    @Test
    void validateAccessTokenReturnsActiveSession() throws Exception {
        UserSession session = sessionSecurityService.createSession(
                UUID.randomUUID(),
                "device-2",
                "fingerprint-2",
                "JUnit",
                "127.0.0.1");

        UserSession validated = sessionSecurityService.validateAccessToken(session.getAccessToken());

        assertEquals(session.getSessionId(), validated.getSessionId());
        assertFalse(Boolean.TRUE.equals(validated.getIsRevoked()));
    }

    @Test
    void refreshAccessTokenRotatesAccessTokenAndRejectsWrongDevice() throws Exception {
        UserSession session = sessionSecurityService.createSession(
                UUID.randomUUID(),
                "device-3",
                "fingerprint-3",
                "JUnit",
                "127.0.0.1");

        String oldAccessToken = session.getAccessToken();
        UserSession refreshed = sessionSecurityService.refreshAccessToken(session.getRefreshToken(), "fingerprint-3");

        assertNotEquals(oldAccessToken, refreshed.getAccessToken());
        assertThrows(SessionSecurityService.SessionSecurityException.class, () ->
                sessionSecurityService.validateAccessToken(oldAccessToken));
        assertThrows(SessionSecurityService.SessionSecurityException.class, () ->
                sessionSecurityService.refreshAccessToken(refreshed.getRefreshToken(), "wrong-fingerprint"));
    }

    @Test
    void revokeAndLogoutInvalidateSessions() {
        UserSession session = sessionSecurityService.createSession(
                UUID.randomUUID(),
                "device-4",
                "fingerprint-4",
                "JUnit",
                "127.0.0.1");

        sessionSecurityService.revokeSession(session.getSessionId(), "manual_revoke");
        assertThrows(SessionSecurityService.SessionSecurityException.class, () ->
                sessionSecurityService.validateAccessToken(session.getAccessToken()));

        UserSession another = sessionSecurityService.createSession(
                session.getUserId(),
                "device-5",
                "fingerprint-5",
                "JUnit",
                "127.0.0.1");
        assertDoesNotThrow(() -> sessionSecurityService.logout(another.getAccessToken()));
        assertThrows(SessionSecurityService.SessionSecurityException.class, () ->
                sessionSecurityService.validateAccessToken(another.getAccessToken()));
    }

    @Test
    void revokeAllUserSessionsClearsActiveSessions() {
        UUID userId = UUID.randomUUID();
        sessionSecurityService.createSession(userId, "device-a", "fingerprint-a", "JUnit", "127.0.0.1");
        sessionSecurityService.createSession(userId, "device-b", "fingerprint-b", "JUnit", "127.0.0.1");

        sessionSecurityService.revokeAllUserSessions(userId, "security_event");

        assertTrue(sessionSecurityService.getUserActiveSessions(userId).isEmpty());
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = SessionSecurityService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(sessionSecurityService, value);
    }
}
