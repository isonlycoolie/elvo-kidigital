package com.elvo.identity.service;

import com.elvo.identity.dto.request.FastLoginGenerateRequest;
import com.elvo.identity.dto.request.FastLoginVerifyRequest;
import com.elvo.identity.dto.response.FastLoginChallengeResponse;
import com.elvo.identity.dto.response.FastLoginResponse;

public interface FastLoginService {

    FastLoginChallengeResponse generateFastLoginPin(FastLoginGenerateRequest request);

    FastLoginResponse verifyFastLogin(FastLoginVerifyRequest request);
}
