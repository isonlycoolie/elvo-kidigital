package com.elvo.webdashboard.dto.response;

import java.util.UUID;

public class UserProfileResponse {

    private UUID userId;
    private String displayName;
    private String locale;
    private String timezone;

    public UserProfileResponse() {
    }

    public UserProfileResponse(UUID userId, String displayName, String locale, String timezone) {
        this.userId = userId;
        this.displayName = displayName;
        this.locale = locale;
        this.timezone = timezone;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
