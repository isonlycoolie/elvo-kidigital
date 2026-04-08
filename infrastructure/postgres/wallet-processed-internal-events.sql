CREATE TABLE IF NOT EXISTS processed_internal_events (
    event_id VARCHAR(128) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    source_service VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_internal_events_processed_at
    ON processed_internal_events (processed_at DESC);
