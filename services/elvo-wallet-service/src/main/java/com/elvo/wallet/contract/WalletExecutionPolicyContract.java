package com.elvo.wallet.contract;

/**
 * Shared execution policy contract v1 for account and identity validation outcomes.
 */
public record WalletExecutionPolicyContract(
        String version,
        Decision decision,
        String sourceSystem,
        String reasonCode,
        String reasonDetail,
        String accountStatus,
        String kycStatus,
        String identityState
) {

    public static final String V1 = "v1";

    public WalletExecutionPolicyContract {
        version = (version == null || version.isBlank()) ? V1 : version;
        sourceSystem = sourceSystem == null ? "wallet-service" : sourceSystem;
        reasonCode = reasonCode == null ? "" : reasonCode;
        reasonDetail = reasonDetail == null ? "" : reasonDetail;
        accountStatus = accountStatus == null ? "" : accountStatus;
        kycStatus = kycStatus == null ? "" : kycStatus;
        identityState = identityState == null ? "" : identityState;
    }

    public static WalletExecutionPolicyContract allow(String sourceSystem,
                                                      String reasonCode,
                                                      String reasonDetail,
                                                      String accountStatus,
                                                      String kycStatus,
                                                      String identityState) {
        return new WalletExecutionPolicyContract(V1, Decision.ALLOW, sourceSystem, reasonCode, reasonDetail, accountStatus, kycStatus, identityState);
    }

    public static WalletExecutionPolicyContract deny(String sourceSystem,
                                                     String reasonCode,
                                                     String reasonDetail,
                                                     String accountStatus,
                                                     String kycStatus,
                                                     String identityState) {
        return new WalletExecutionPolicyContract(V1, Decision.DENY, sourceSystem, reasonCode, reasonDetail, accountStatus, kycStatus, identityState);
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }

    public String toFailureMessage() {
        if (reasonCode.isBlank()) {
            return reasonDetail.isBlank() ? "Execution policy denied" : reasonDetail;
        }
        if (reasonDetail.isBlank()) {
            return reasonCode;
        }
        return reasonCode + ": " + reasonDetail;
    }

    public enum Decision {
        ALLOW,
        DENY
    }
}
