package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.elvo.identity.contract.RiskDecisionContract;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.entity.User;

class WeightedRiskScoringServiceUnitTest {

    @Test
    void highRiskLoginShouldReturnBlockDecision() {
        WeightedRiskScoringService service = new WeightedRiskScoringService(
                50,
                80,
                25,
                25,
                10,
                20,
                10);

        User user = new User();
        user.setMfaEnabled(false);
        user.setSuspiciousActivityCount(5);

        LoginRequest request = new LoginRequest();
        request.setDeviceId("new-device");
        request.setSourceIp(null);
        request.setSourceUserAgent(null);

        RiskDecisionContract decision = service.evaluateLogin(user, request, false);
        assertEquals(RiskDecisionContract.Decision.BLOCK, decision.decision());
    }

    @Test
    void lowRiskOnboardingShouldReturnAllowDecision() {
        WeightedRiskScoringService service = new WeightedRiskScoringService(
                50,
                80,
                25,
                25,
                10,
                20,
                10);

        RiskDecisionContract decision = service.evaluateOnboarding(true, "127.0.0.1", "JUnit");
        assertEquals(RiskDecisionContract.Decision.ALLOW, decision.decision());
    }
}
