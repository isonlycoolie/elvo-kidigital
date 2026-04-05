package com.elvo.identity.service;

import java.util.List;
import java.util.UUID;

import com.elvo.identity.dto.request.SessionCreateRequest;
import com.elvo.identity.dto.response.SessionInfoResponse;
import com.elvo.identity.dto.response.SessionTokenResponse;

public interface SessionManagementService {

    SessionTokenResponse createSession(SessionCreateRequest request);

    int revokeSession(UUID sessionId);

    int revokeAllUserSessions(UUID userId);

    List<SessionInfoResponse> getActiveSessions(UUID userId);
}
