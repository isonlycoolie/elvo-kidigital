-- ELVO Wallet reliable event outbox
-- Execute as DBA before enabling outbox dispatch in production.

BEGIN;

CREATE TABLE IF NOT EXISTS wallet_event_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    request_id VARCHAR(128),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wallet_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_outbox_status_next_attempt
    ON wallet_event_outbox (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_wallet_outbox_event_type
    ON wallet_event_outbox (event_type, created_at DESC);

ALTER TABLE wallet_event_outbox OWNER TO elvo_wallet_owner;
GRANT SELECT, INSERT, UPDATE ON wallet_event_outbox TO elvo_wallet_app;
GRANT SELECT ON wallet_event_outbox TO elvo_wallet_ro;

COMMIT;
