package com.elvo.billing.security;

import com.elvo.billing.entity.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionSecurityService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionSecurityService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${elvo.security.session.access-token-duration-seconds:900}")
    private long accessTokenDurationSeconds;

    @Value("${elvo.security.session.refresh-token-duration-seconds:604800}")
    private long refreshTokenDurationSeconds;

    @Value("${elvo.security.session.max-concurrent-sessions:5}")
    private int maxConcurrentSessions;

    private final Map<String, UserSession> sessionCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, UserSession> eldest) {
            return size() > 10000;
        }
    };

    public synchronized UserSession createSession(UUID userId, String deviceId, String deviceFingerprint, String userAgent, String sourceIp) {
        List<UserSession> activeSessions = getUserActiveSessions(userId);
        if (activeSessions.size() >= maxConcurrentSessions) {
            activeSessions.stream()
                    .min(Comparator.comparing(UserSession::getCreatedAt))
                    .ifPresent(oldest -> revokeSession(oldest.getSessionId(), "max_sessions_exceeded"));
        }

        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(userId);
        session.setDeviceId(deviceId);
        session.setDeviceFingerprint(deviceFingerprint);
        session.setUserAgent(userAgent);
        session.setSourceIp(sourceIp);
        session.setAccessToken(generateToken());
        session.setRefreshToken(generateToken());
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(accessTokenDurationSeconds));
        session.setRefreshTokenExpiresAt(Instant.now().plusSeconds(refreshTokenDurationSeconds));
        session.setIsRevoked(false);
        session.setLastActivityAt(Instant.now());

        sessionCache.put(session.getAccessToken(), session);
        LOG.info("Session created userId={} sessionId={} deviceId={}", userId, session.getSessionId(), deviceId);
        return session;
    }

    public synchronized UserSession validateAccessToken(String accessToken) throws SessionSecurityException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new SessionSecurityException("Missing access token");
        }

        UserSession session = sessionCache.get(accessToken);
        if (session == null) {
            throw new SessionSecurityException("Invalid or unknown access token");
        }
        if (Boolean.TRUE.equals(session.getIsRevoked())) {
            throw new SessionSecurityException("Session has been revoked: " + session.getRevocationReason());
        }
        if (session.isExpired()) {
            throw new SessionSecurityException("Access token has expired");
        }

        session.setLastActivityAt(Instant.now());
        return session;
    }

    public synchronized UserSession refreshAccessToken(String refreshToken, String currentDeviceFingerprint) throws SessionSecurityException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessionSecurityException("Missing refresh token");
        }

        UserSession session = sessionCache.values().stream()
                .filter(candidate -> refreshToken.equals(candidate.getRefreshToken()))
                .findFirst()
                .orElseThrow(() -> new SessionSecurityException("Invalid or unknown refresh token"));

        if (Boolean.TRUE.equals(session.getIsRevoked())) {
            throw new SessionSecurityException("Session has been revoked");
        }
        if (Instant.now().isAfter(session.getRefreshTokenExpiresAt())) {
            throw new SessionSecurityException("Refresh token has expired");
        }
        if (currentDeviceFingerprint == null || !currentDeviceFingerprint.equals(session.getDeviceFingerprint())) {
            revokeSession(session.getSessionId(), "device_mismatch_on_refresh");
            throw new SessionSecurityException("Device validation failed. Session revoked.");
        }

        sessionCache.remove(session.getAccessToken());
        session.setAccessToken(generateToken());
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(accessTokenDurationSeconds));
        session.setLastActivityAt(Instant.now());
        sessionCache.put(session.getAccessToken(), session);
        LOG.info("Access token refreshed userId={} sessionId={}", session.getUserId(), session.getSessionId());
        return session;
    }

    public synchronized void logout(String accessToken) throws SessionSecurityException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new SessionSecurityException("Missing access token for logout");
        }

        UserSession session = sessionCache.get(accessToken);
        if (session != null) {
            revokeSession(session.getSessionId(), "user_logout");
            sessionCache.remove(accessToken);
        }
    }

    public synchronized void revokeSession(UUID sessionId, String reason) {
        UserSession session = sessionCache.values().stream()
                .filter(candidate -> sessionId.equals(candidate.getSessionId()))
                .findFirst()
                .orElse(null);
        if (session == null) {
            return;
        }

        session.setIsRevoked(true);
        session.setRevokedAt(Instant.now());
        session.setRevocationReason(reason);
        sessionCache.remove(session.getAccessToken());
        LOG.info("Session revoked userId={} sessionId={} reason={}", session.getUserId(), sessionId, reason);
    }

    public synchronized void revokeAllUserSessions(UUID userId, String reason) {
        new ArrayList<>(sessionCache.values()).stream()
                .filter(session -> userId.equals(session.getUserId()))
                .forEach(session -> revokeSession(session.getSessionId(), reason));
    }

    public synchronized List<UserSession> getUserActiveSessions(UUID userId) {
        return sessionCache.values().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .filter(session -> !Boolean.TRUE.equals(session.getIsRevoked()))
                .filter(session -> !session.isExpired())
                .toList();
    }

    public static class SessionSecurityException extends Exception {
        public SessionSecurityException(String message) {
            super(message);
        }

        public SessionSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
