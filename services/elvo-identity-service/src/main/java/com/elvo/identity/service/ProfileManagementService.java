package com.elvo.identity.service;

import com.elvo.identity.dto.request.ProfileUpdateRequest;
import com.elvo.identity.dto.response.ProfileResponse;

public interface ProfileManagementService {

    ProfileResponse updateProfile(ProfileUpdateRequest request);
}
