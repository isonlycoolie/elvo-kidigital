-- ELVO Wallet encryption baseline controls
-- Execute as DBA / platform operator.

BEGIN;

-- Ensure pgcrypto is available for cryptographic operations and future key-rotation routines.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Track encryption baseline activation for operational audits.
CREATE TABLE IF NOT EXISTS wallet_security_baseline (
    id BIGSERIAL PRIMARY KEY,
    control_name VARCHAR(128) NOT NULL,
    control_value VARCHAR(256) NOT NULL,
    enforced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO wallet_security_baseline (control_name, control_value)
VALUES ('field_encryption', 'enabled')
ON CONFLICT DO NOTHING;

COMMIT;
