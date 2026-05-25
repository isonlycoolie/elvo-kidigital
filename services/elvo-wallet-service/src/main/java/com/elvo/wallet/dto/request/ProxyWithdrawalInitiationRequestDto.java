package com.elvo.wallet.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProxyWithdrawalInitiationRequestDto {

    @NotBlank(message = "Beneficiary name is required")
    @Size(max = 128, message = "Beneficiary name must be 128 characters or fewer")
    private String beneficiaryName;

    @NotBlank(message = "Beneficiary phone is required")
    @Size(max = 32, message = "Beneficiary phone must be 32 characters or fewer")
    @Pattern(regexp = "^[+0-9]{7,32}$", message = "Beneficiary phone must contain only digits and an optional leading plus")
    private String beneficiaryPhone;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000.0000", message = "Proxy withdrawal amount must not exceed 1000.0000")
    @Digits(integer = 15, fraction = 4, message = "Amount must have up to 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 128, message = "Idempotency key must be between 8 and 128 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String idempotencyKey;

    @Size(max = 128, message = "Reference must be 128 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Reference may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String reference;

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryPhone() {
        return beneficiaryPhone;
    }

    public void setBeneficiaryPhone(String beneficiaryPhone) {
        this.beneficiaryPhone = beneficiaryPhone;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
