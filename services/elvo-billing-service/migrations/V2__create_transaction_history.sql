CREATE TABLE IF NOT EXISTS transaction_history (
    history_id VARCHAR(64) PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transaction_history_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions (transaction_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transaction_history_transaction_id
    ON transaction_history (transaction_id);

CREATE INDEX IF NOT EXISTS idx_transaction_history_created_at
    ON transaction_history (created_at);
