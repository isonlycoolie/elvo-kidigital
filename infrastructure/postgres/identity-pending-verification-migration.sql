-- ELVO Identity pending verification lifecycle migration
-- Safe to run multiple times.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS mobile_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS email_verified_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS mobile_verified_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS verification_status varchar(32) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS verification_deadline timestamp with time zone,
    ADD COLUMN IF NOT EXISTS downstream_provisioned boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS downstream_provisioned_at timestamp with time zone;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_verification_status'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_verification_status
            CHECK (verification_status IN ('UNVERIFIED', 'PARTIAL', 'VERIFIED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_account_status'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_account_status
            CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'EXPIRED', 'LOCKED', 'SUSPENDED', 'DISABLED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_verification_status ON users (verification_status);
CREATE INDEX IF NOT EXISTS idx_users_verification_deadline ON users (verification_deadline);
