package com.elvo.billing.contract;

import java.util.List;

/**
 * Shared risk decision contract v1 for cross-service policy alignment.
 */
public record RiskDecisionContract(
    String version,
    Decision decision,
    int riskScore,
    boolean stepUpRequired,
    List<String> reasons
) {

    public static final String V1 = "v1";

    public RiskDecisionContract {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("riskScore must be between 0 and 100");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        version = (version == null || version.isBlank()) ? V1 : version;
    }

    public static RiskDecisionContract allow(int riskScore, List<String> reasons) {
        return new RiskDecisionContract(V1, Decision.ALLOW, riskScore, false, reasons);
    }

    public static RiskDecisionContract challenge(int riskScore, List<String> reasons) {
        return new RiskDecisionContract(V1, Decision.CHALLENGE, riskScore, true, reasons);
    }

    public static RiskDecisionContract block(int riskScore, List<String> reasons) {
        return new RiskDecisionContract(V1, Decision.BLOCK, riskScore, true, reasons);
    }

    public enum Decision {
        ALLOW,
        CHALLENGE,
        BLOCK
    }
}