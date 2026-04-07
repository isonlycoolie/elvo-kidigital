CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(64) PRIMARY KEY,
    service_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    wallet_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_reference_id
    ON transactions (reference_id);

CREATE INDEX IF NOT EXISTS idx_transactions_status
    ON transactions (status);
