package com.elvo.identity.service;

import com.elvo.identity.dto.request.EacGenerateRequest;
import com.elvo.identity.dto.request.EacVerifyRequest;
import com.elvo.identity.dto.response.EacGenerateResponse;

public interface EacManagementService {

    EacGenerateResponse generateEac(EacGenerateRequest request);

    boolean verifyEac(EacVerifyRequest request);
}
