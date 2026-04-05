package com.elvo.identity.service;

import com.elvo.identity.dto.request.RegistrationRequest;
import com.elvo.identity.dto.response.RegistrationResponse;

public interface RegistrationService {

    RegistrationResponse register(RegistrationRequest request);
}
