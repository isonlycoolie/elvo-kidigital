package com.elvo.identity.service;

import com.elvo.identity.dto.request.EmailRegistrationRequest;
import com.elvo.identity.dto.request.MobileRegistrationRequest;
import com.elvo.identity.dto.request.RegistrationRequest;
import com.elvo.identity.dto.response.RegistrationResponse;

public interface RegistrationService {

    RegistrationResponse register(RegistrationRequest request);

    RegistrationResponse registerEmail(EmailRegistrationRequest request);

    RegistrationResponse registerMobile(MobileRegistrationRequest request);
}
