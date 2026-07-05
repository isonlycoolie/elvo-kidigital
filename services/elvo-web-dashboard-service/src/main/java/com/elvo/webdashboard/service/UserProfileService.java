package com.elvo.webdashboard.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.elvo.webdashboard.dto.request.UserProfileUpdateRequest;
import com.elvo.webdashboard.dto.response.UserProfileResponse;

@Service
public class UserProfileService {

    private final ConcurrentHashMap<UUID, UserProfileResponse> profiles = new ConcurrentHashMap<>();

    public UserProfileResponse getProfile(UUID userId) {
        return profiles.computeIfAbsent(userId, id -> new UserProfileResponse(id, "ELVO User", "en-TZ", "Africa/Dar_es_Salaam"));
    }

    public UserProfileResponse upsertProfile(UUID userId, UserProfileUpdateRequest request) {
        UserProfileResponse existing = getProfile(userId);
        if (request.getDisplayName() != null) {
            existing.setDisplayName(request.getDisplayName());
        }
        if (request.getLocale() != null) {
            existing.setLocale(request.getLocale());
        }
        if (request.getTimezone() != null) {
            existing.setTimezone(request.getTimezone());
        }
        profiles.put(userId, existing);
        return existing;
    }
}
