package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.service.model.StepUpMethod;
import com.elvo.wallet.service.model.WithdrawalMode;

@Service
public class StepUpAuthenticationService {

    private final BigDecimal transferThreshold;
    private final BigDecimal withdrawalThreshold;

    public StepUpAuthenticationService(
            @Value("${elvo.security.step-up.transfer-threshold:500.00}") BigDecimal transferThreshold,
            @Value("${elvo.security.step-up.withdrawal-threshold:250.00}") BigDecimal withdrawalThreshold
    ) {
        this.transferThreshold = transferThreshold;
        this.withdrawalThreshold = withdrawalThreshold;
    }

    public boolean requiresStepUpForWithdrawal(BigDecimal amount, WithdrawalMode mode) {
        if (amount == null || mode == null) {
            return true;
        }
        return amount.compareTo(withdrawalThreshold) >= 0 || mode == WithdrawalMode.DEVICE_FREE || mode == WithdrawalMode.OTHER_NUMBER;
    }

    public boolean requiresStepUpForTransfer(BigDecimal amount) {
        return amount != null && amount.compareTo(transferThreshold) >= 0;
    }

    public boolean isValidConfirmation(String stepUpMethod, String stepUpToken, String challengeBinding) {
        if (stepUpMethod == null || stepUpToken == null || challengeBinding == null) {
            return false;
        }

        StepUpMethod method;
        try {
            method = StepUpMethod.valueOf(stepUpMethod.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }

        if (stepUpToken.isBlank() || challengeBinding.isBlank()) {
            return false;
        }

        return method != null && stepUpToken.contains(challengeBinding);
    }
}