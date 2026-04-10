package com.elvo.identity.service;

import com.elvo.identity.contract.RiskDecisionContract;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.entity.User;

public interface RiskScoringService {

    RiskDecisionContract evaluateLogin(User user, LoginRequest request, boolean knownDevice);

    RiskDecisionContract evaluateOnboarding(boolean mfaEnabled, String sourceIp, String sourceUserAgent);
}