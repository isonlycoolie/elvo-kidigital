-- ELVO Identity OTP query index hardening migration
-- Safe to run multiple times.

CREATE INDEX IF NOT EXISTS idx_verification_otps_user_purpose_status_created
    ON verification_otps (user_id, purpose, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_verification_otps_user_purpose_created
    ON verification_otps (user_id, purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_verification_otps_user_purpose_request
    ON verification_otps (user_id, purpose, request_id);
