-- ELVO Billing Service PostgreSQL schema
-- Phase 0 task 1: setup billing database
-- Execute as a privileged database administrator.

BEGIN;

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    bill_category VARCHAR(64) NOT NULL,
    service_code VARCHAR(64) NOT NULL,
    reference_number VARCHAR(128) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    customer_phone TEXT,
    customer_name TEXT,
    metadata TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    external_reference VARCHAR(255),
    receipt_number VARCHAR(128),
    paid_amount NUMERIC(19, 2),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bill_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bill_payments_paid_amount_non_negative CHECK (paid_amount IS NULL OR paid_amount >= 0),
    CONSTRAINT uq_bill_payments_request_id UNIQUE (request_id),
    CONSTRAINT uq_bill_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_bill_payments_reference_number
    ON bill_payments (reference_number);

CREATE INDEX IF NOT EXISTS idx_bill_payments_service_code_reference_number
    ON bill_payments (service_code, reference_number);

CREATE INDEX IF NOT EXISTS idx_bill_payments_status_created_at
    ON bill_payments (status, created_at DESC);

CREATE TABLE IF NOT EXISTS bill_lookups (
    lookup_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    bill_category VARCHAR(64) NOT NULL,
    service_code VARCHAR(64) NOT NULL,
    reference_number VARCHAR(128) NOT NULL,
    customer_phone TEXT,
    metadata TEXT NOT NULL DEFAULT '{}',
    lookup_status VARCHAR(32) NOT NULL,
    customer_name TEXT,
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    description TEXT,
    bill_items TEXT,
    raw_provider_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bill_lookups_request_id UNIQUE (request_id)
);

CREATE INDEX IF NOT EXISTS idx_bill_lookups_reference_number
    ON bill_lookups (reference_number);

CREATE INDEX IF NOT EXISTS idx_bill_lookups_service_code_reference_number
    ON bill_lookups (service_code, reference_number);

CREATE INDEX IF NOT EXISTS idx_bill_lookups_lookup_status_created_at
    ON bill_lookups (lookup_status, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_history (
    history_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES bill_payments (payment_id) ON DELETE CASCADE,
    request_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    adapter_name VARCHAR(128),
    adapter_reference VARCHAR(255),
    response_code VARCHAR(64),
    response_message VARCHAR(512),
    metadata TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_history_payment_id_created_at
    ON payment_history (payment_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_history_request_id_created_at
    ON payment_history (request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_history_event_type_created_at
    ON payment_history (event_type, created_at DESC);

COMMIT;