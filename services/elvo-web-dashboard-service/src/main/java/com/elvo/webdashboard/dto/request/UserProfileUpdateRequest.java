package com.elvo.webdashboard.dto.request;

import jakarta.validation.constraints.Size;

public class UserProfileUpdateRequest {

    @Size(max = 120)
    private String displayName;

    @Size(max = 10)
    private String locale;

    @Size(max = 64)
    private String timezone;

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
