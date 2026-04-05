package com.elvo.identity.service;

import com.elvo.identity.dto.request.EspGenerateRequest;
import com.elvo.identity.dto.request.EspVerifyRequest;
import com.elvo.identity.dto.response.EspGenerateResponse;

public interface EspManagementService {

    EspGenerateResponse generateEsp(EspGenerateRequest request);

    EspGenerateResponse updateEsp(EspGenerateRequest request);

    boolean verifyEsp(EspVerifyRequest request);
}
