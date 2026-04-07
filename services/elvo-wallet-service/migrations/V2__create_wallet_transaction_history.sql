CREATE TABLE IF NOT EXISTS wallet_transaction_history (
    history_id VARCHAR(64) PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_wallet_tx_history_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES wallet_transactions (transaction_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wallet_tx_history_transaction_id
    ON wallet_transaction_history (transaction_id);

CREATE INDEX IF NOT EXISTS idx_wallet_tx_history_created_at
    ON wallet_transaction_history (created_at);
