package com.elvo.identity.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.identity.contract.RiskDecisionContract;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.entity.User;
import com.elvo.identity.service.RiskScoringService;

@Service
public class WeightedRiskScoringService implements RiskScoringService {

    private final int challengeThreshold;
    private final int blockThreshold;
    private final int missingIpWeight;
    private final int unknownDeviceWeight;
    private final int suspiciousActivityWeight;
    private final int mfaDisabledWeight;
    private final int missingUserAgentWeight;

    public WeightedRiskScoringService(
            @Value("${elvo.security.risk.challenge-threshold:50}") int challengeThreshold,
            @Value("${elvo.security.risk.block-threshold:80}") int blockThreshold,
            @Value("${elvo.security.risk.weights.missing-ip:25}") int missingIpWeight,
            @Value("${elvo.security.risk.weights.unknown-device:25}") int unknownDeviceWeight,
            @Value("${elvo.security.risk.weights.suspicious-activity:10}") int suspiciousActivityWeight,
            @Value("${elvo.security.risk.weights.mfa-disabled:20}") int mfaDisabledWeight,
            @Value("${elvo.security.risk.weights.missing-user-agent:10}") int missingUserAgentWeight) {
        this.challengeThreshold = challengeThreshold;
        this.blockThreshold = blockThreshold;
        this.missingIpWeight = missingIpWeight;
        this.unknownDeviceWeight = unknownDeviceWeight;
        this.suspiciousActivityWeight = suspiciousActivityWeight;
        this.mfaDisabledWeight = mfaDisabledWeight;
        this.missingUserAgentWeight = missingUserAgentWeight;
    }

    @Override
    public RiskDecisionContract evaluateLogin(User user, LoginRequest request, boolean knownDevice) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (isBlank(request.getSourceIp())) {
            score += missingIpWeight;
            reasons.add("MISSING_SOURCE_IP");
        }
        if (!knownDevice) {
            score += unknownDeviceWeight;
            reasons.add("UNKNOWN_DEVICE");
        }
        int suspicious = Math.max(0, user.getSuspiciousActivityCount());
        if (suspicious > 0) {
            score += Math.min(30, suspicious * suspiciousActivityWeight);
            reasons.add("ELEVATED_SUSPICIOUS_ACTIVITY");
        }
        if (!user.isMfaEnabled()) {
            score += mfaDisabledWeight;
            reasons.add("MFA_DISABLED");
        }
        if (isBlank(request.getSourceUserAgent())) {
            score += missingUserAgentWeight;
            reasons.add("MISSING_USER_AGENT");
        }
        return toDecision(score, reasons);
    }

    @Override
    public RiskDecisionContract evaluateOnboarding(boolean mfaEnabled, String sourceIp, String sourceUserAgent) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (isBlank(sourceIp)) {
            score += missingIpWeight;
            reasons.add("MISSING_SOURCE_IP");
        }
        if (!mfaEnabled) {
            score += mfaDisabledWeight;
            reasons.add("MFA_DISABLED");
        }
        if (isBlank(sourceUserAgent)) {
            score += missingUserAgentWeight;
            reasons.add("MISSING_USER_AGENT");
        }
        return toDecision(score, reasons);
    }

    private RiskDecisionContract toDecision(int score, List<String> reasons) {
        int bounded = Math.max(0, Math.min(100, score));
        if (bounded >= blockThreshold) {
            return RiskDecisionContract.block(bounded, reasons);
        }
        if (bounded >= challengeThreshold) {
            return RiskDecisionContract.challenge(bounded, reasons);
        }
        return RiskDecisionContract.allow(bounded, reasons);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}