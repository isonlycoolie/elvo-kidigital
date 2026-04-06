package com.elvo.identity.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_ean", columnNames = "ean"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        },
        indexes = {
                @Index(name = "idx_users_account_status", columnList = "account_status"),
            @Index(name = "idx_users_verification_status", columnList = "verification_status"),
                @Index(name = "idx_users_created_at", columnList = "created_at")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "ean", nullable = false, length = 64)
    private String ean;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "hashed_password", nullable = false, length = 255)
    private String hashedPassword;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "esp_enabled", nullable = false)
    private boolean espEnabled;

    @Column(name = "esp_hash", length = 255)
    private String espHash;

    @Column(name = "esp_expires_at")
    private Instant espExpiresAt;

    @Column(name = "esp_failed_attempts", nullable = false)
    private int espFailedAttempts;

    @Column(name = "esp_last_requested_at")
    private Instant espLastRequestedAt;

    @Column(name = "eac_hash", length = 255)
    private String eacHash;

    @Column(name = "eac_expires_at")
    private Instant eacExpiresAt;

    @Column(name = "eac_failed_attempts", nullable = false)
    private int eacFailedAttempts;

    @Column(name = "eac_last_requested_at")
    private Instant eacLastRequestedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @Column(name = "security_last_event_at")
    private Instant securityLastEventAt;

    @Column(name = "suspicious_activity_count", nullable = false)
    private int suspiciousActivityCount;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_reset_hash", length = 255)
    private String passwordResetHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Column(name = "password_reset_failed_attempts", nullable = false)
    private int passwordResetFailedAttempts;

    @Column(name = "password_reset_last_requested_at")
    private Instant passwordResetLastRequestedAt;

    @Column(name = "fast_login_pin_hash", length = 255)
    private String fastLoginPinHash;

    @Column(name = "fast_login_expires_at")
    private Instant fastLoginExpiresAt;

    @Column(name = "fast_login_failed_attempts", nullable = false)
    private int fastLoginFailedAttempts;

    @Column(name = "fast_login_last_requested_at")
    private Instant fastLoginLastRequestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Column(name = "verification_deadline")
    private Instant verificationDeadline;

    @Column(name = "downstream_provisioned", nullable = false)
    private boolean downstreamProvisioned;

    @Column(name = "downstream_provisioned_at")
    private Instant downstreamProvisionedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AccountStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        EXPIRED,
        LOCKED,
        SUSPENDED,
        DISABLED
    }

    public enum VerificationStatus {
        UNVERIFIED,
        PARTIAL,
        VERIFIED
    }

    public UUID getId() {
        return id;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isMobileVerified() {
        return mobileVerified;
    }

    public void setMobileVerified(boolean mobileVerified) {
        this.mobileVerified = mobileVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public Instant getMobileVerifiedAt() {
        return mobileVerifiedAt;
    }

    public void setMobileVerifiedAt(Instant mobileVerifiedAt) {
        this.mobileVerifiedAt = mobileVerifiedAt;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public boolean isEspEnabled() {
        return espEnabled;
    }

    public void setEspEnabled(boolean espEnabled) {
        this.espEnabled = espEnabled;
    }

    public String getEspHash() {
        return espHash;
    }

    public void setEspHash(String espHash) {
        this.espHash = espHash;
    }

    public Instant getEspExpiresAt() {
        return espExpiresAt;
    }

    public void setEspExpiresAt(Instant espExpiresAt) {
        this.espExpiresAt = espExpiresAt;
    }

    public int getEspFailedAttempts() {
        return espFailedAttempts;
    }

    public void setEspFailedAttempts(int espFailedAttempts) {
        this.espFailedAttempts = espFailedAttempts;
    }

    public Instant getEspLastRequestedAt() {
        return espLastRequestedAt;
    }

    public void setEspLastRequestedAt(Instant espLastRequestedAt) {
        this.espLastRequestedAt = espLastRequestedAt;
    }

    public String getEacHash() {
        return eacHash;
    }

    public void setEacHash(String eacHash) {
        this.eacHash = eacHash;
    }

    public Instant getEacExpiresAt() {
        return eacExpiresAt;
    }

    public void setEacExpiresAt(Instant eacExpiresAt) {
        this.eacExpiresAt = eacExpiresAt;
    }

    public int getEacFailedAttempts() {
        return eacFailedAttempts;
    }

    public void setEacFailedAttempts(int eacFailedAttempts) {
        this.eacFailedAttempts = eacFailedAttempts;
    }

    public Instant getEacLastRequestedAt() {
        return eacLastRequestedAt;
    }

    public void setEacLastRequestedAt(Instant eacLastRequestedAt) {
        this.eacLastRequestedAt = eacLastRequestedAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public Instant getSecurityLastEventAt() {
        return securityLastEventAt;
    }

    public void setSecurityLastEventAt(Instant securityLastEventAt) {
        this.securityLastEventAt = securityLastEventAt;
    }

    public int getSuspiciousActivityCount() {
        return suspiciousActivityCount;
    }

    public void setSuspiciousActivityCount(int suspiciousActivityCount) {
        this.suspiciousActivityCount = suspiciousActivityCount;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getPasswordResetHash() {
        return passwordResetHash;
    }

    public void setPasswordResetHash(String passwordResetHash) {
        this.passwordResetHash = passwordResetHash;
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) {
        this.passwordResetExpiresAt = passwordResetExpiresAt;
    }

    public int getPasswordResetFailedAttempts() {
        return passwordResetFailedAttempts;
    }

    public void setPasswordResetFailedAttempts(int passwordResetFailedAttempts) {
        this.passwordResetFailedAttempts = passwordResetFailedAttempts;
    }

    public Instant getPasswordResetLastRequestedAt() {
        return passwordResetLastRequestedAt;
    }

    public void setPasswordResetLastRequestedAt(Instant passwordResetLastRequestedAt) {
        this.passwordResetLastRequestedAt = passwordResetLastRequestedAt;
    }

    public String getFastLoginPinHash() {
        return fastLoginPinHash;
    }

    public void setFastLoginPinHash(String fastLoginPinHash) {
        this.fastLoginPinHash = fastLoginPinHash;
    }

    public Instant getFastLoginExpiresAt() {
        return fastLoginExpiresAt;
    }

    public void setFastLoginExpiresAt(Instant fastLoginExpiresAt) {
        this.fastLoginExpiresAt = fastLoginExpiresAt;
    }

    public int getFastLoginFailedAttempts() {
        return fastLoginFailedAttempts;
    }

    public void setFastLoginFailedAttempts(int fastLoginFailedAttempts) {
        this.fastLoginFailedAttempts = fastLoginFailedAttempts;
    }

    public Instant getFastLoginLastRequestedAt() {
        return fastLoginLastRequestedAt;
    }

    public void setFastLoginLastRequestedAt(Instant fastLoginLastRequestedAt) {
        this.fastLoginLastRequestedAt = fastLoginLastRequestedAt;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Instant getVerificationDeadline() {
        return verificationDeadline;
    }

    public void setVerificationDeadline(Instant verificationDeadline) {
        this.verificationDeadline = verificationDeadline;
    }

    public boolean isDownstreamProvisioned() {
        return downstreamProvisioned;
    }

    public void setDownstreamProvisioned(boolean downstreamProvisioned) {
        this.downstreamProvisioned = downstreamProvisioned;
    }

    public Instant getDownstreamProvisionedAt() {
        return downstreamProvisionedAt;
    }

    public void setDownstreamProvisionedAt(Instant downstreamProvisionedAt) {
        this.downstreamProvisionedAt = downstreamProvisionedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
