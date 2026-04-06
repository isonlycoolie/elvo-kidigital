-- ELVO Identity verification OTP table migration
-- Safe to run multiple times.

CREATE TABLE IF NOT EXISTS verification_otps (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    channel varchar(16) NOT NULL,
    destination varchar(255) NOT NULL,
    otp_hash varchar(255) NOT NULL,
    purpose varchar(32) NOT NULL,
    status varchar(16) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    attempt_count integer NOT NULL DEFAULT 0,
    resend_count integer NOT NULL DEFAULT 0,
    request_id varchar(64),
    correlation_id varchar(64),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_verification_otps_channel'
    ) THEN
        ALTER TABLE verification_otps
            ADD CONSTRAINT chk_verification_otps_channel
            CHECK (channel IN ('EMAIL', 'SMS'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_verification_otps_purpose'
    ) THEN
        ALTER TABLE verification_otps
            ADD CONSTRAINT chk_verification_otps_purpose
            CHECK (purpose IN ('EMAIL_VERIFICATION', 'MOBILE_VERIFICATION', 'LOGIN_CHALLENGE', 'RECOVERY'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_verification_otps_status'
    ) THEN
        ALTER TABLE verification_otps
            ADD CONSTRAINT chk_verification_otps_status
            CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED', 'LOCKED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_verification_otps_user_id ON verification_otps (user_id);
CREATE INDEX IF NOT EXISTS idx_verification_otps_destination ON verification_otps (destination);
CREATE INDEX IF NOT EXISTS idx_verification_otps_status ON verification_otps (status);
CREATE INDEX IF NOT EXISTS idx_verification_otps_expires_at ON verification_otps (expires_at);
CREATE INDEX IF NOT EXISTS idx_verification_otps_request_id ON verification_otps (request_id);
