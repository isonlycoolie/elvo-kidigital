-- ELVO Wallet PostgreSQL hardening
-- Row-level security + least-privilege roles
-- Execute as a privileged database administrator.

BEGIN;

-- 1) Roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'elvo_wallet_owner') THEN
        CREATE ROLE elvo_wallet_owner NOLOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'elvo_wallet_app') THEN
        CREATE ROLE elvo_wallet_app LOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'elvo_wallet_ro') THEN
        CREATE ROLE elvo_wallet_ro LOGIN;
    END IF;
END
$$;

-- 2) Baseline schema permissions
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO elvo_wallet_app, elvo_wallet_ro;

-- 3) Table ownership (assumes these tables already exist)
ALTER TABLE IF EXISTS wallets OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS transactions OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS ledger_entries OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS reservations OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS withdrawal_codes OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS audit_logs OWNER TO elvo_wallet_owner;
ALTER TABLE IF EXISTS wallet_audit_events OWNER TO elvo_wallet_owner;

-- 4) Revoke broad table rights
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM elvo_wallet_app;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM elvo_wallet_ro;

-- 5) Least-privilege grants
-- application role: only runtime DML it needs
GRANT SELECT, INSERT, UPDATE ON wallets TO elvo_wallet_app;
GRANT SELECT, INSERT, UPDATE ON transactions TO elvo_wallet_app;
GRANT SELECT, INSERT, UPDATE ON reservations TO elvo_wallet_app;
GRANT SELECT, INSERT, UPDATE ON withdrawal_codes TO elvo_wallet_app;
GRANT SELECT, INSERT ON ledger_entries TO elvo_wallet_app;
GRANT SELECT, INSERT ON audit_logs TO elvo_wallet_app;
GRANT SELECT, INSERT ON wallet_audit_events TO elvo_wallet_app;

-- read-only operational role: query-only
GRANT SELECT ON wallets TO elvo_wallet_ro;
GRANT SELECT ON transactions TO elvo_wallet_ro;
GRANT SELECT ON reservations TO elvo_wallet_ro;
GRANT SELECT ON withdrawal_codes TO elvo_wallet_ro;
GRANT SELECT ON ledger_entries TO elvo_wallet_ro;
GRANT SELECT ON audit_logs TO elvo_wallet_ro;
GRANT SELECT ON wallet_audit_events TO elvo_wallet_ro;

-- Task 32 hardening: ledger tables are append-only for production runtime roles.
REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entries FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entries FROM elvo_wallet_app;
REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entries FROM elvo_wallet_ro;

-- sequences required for inserts (BIGSERIAL tables)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO elvo_wallet_app;

-- 6) Row-level security
ALTER TABLE IF EXISTS wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS withdrawal_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS wallet_audit_events ENABLE ROW LEVEL SECURITY;

-- Force RLS for non-owner access paths
ALTER TABLE IF EXISTS wallets FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS reservations FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS withdrawal_codes FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS audit_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS wallet_audit_events FORCE ROW LEVEL SECURITY;

-- 7) Policies
-- App runtime: full row access (still restricted by table-level grants)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='wallets' AND policyname='wallets_app_all') THEN
        CREATE POLICY wallets_app_all ON wallets FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='transactions' AND policyname='transactions_app_all') THEN
        CREATE POLICY transactions_app_all ON transactions FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='reservations' AND policyname='reservations_app_all') THEN
        CREATE POLICY reservations_app_all ON reservations FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='withdrawal_codes' AND policyname='withdrawal_codes_app_all') THEN
        CREATE POLICY withdrawal_codes_app_all ON withdrawal_codes FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='audit_logs' AND policyname='audit_logs_app_all') THEN
        CREATE POLICY audit_logs_app_all ON audit_logs FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='wallet_audit_events' AND policyname='wallet_audit_events_app_all') THEN
        CREATE POLICY wallet_audit_events_app_all ON wallet_audit_events FOR ALL TO elvo_wallet_app USING (true) WITH CHECK (true);
    END IF;
END
$$;

-- Read-only role: explicit read policies
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='wallets' AND policyname='wallets_ro_select') THEN
        CREATE POLICY wallets_ro_select ON wallets FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='transactions' AND policyname='transactions_ro_select') THEN
        CREATE POLICY transactions_ro_select ON transactions FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='reservations' AND policyname='reservations_ro_select') THEN
        CREATE POLICY reservations_ro_select ON reservations FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='withdrawal_codes' AND policyname='withdrawal_codes_ro_select') THEN
        CREATE POLICY withdrawal_codes_ro_select ON withdrawal_codes FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='audit_logs' AND policyname='audit_logs_ro_select') THEN
        CREATE POLICY audit_logs_ro_select ON audit_logs FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='wallet_audit_events' AND policyname='wallet_audit_events_ro_select') THEN
        CREATE POLICY wallet_audit_events_ro_select ON wallet_audit_events FOR SELECT TO elvo_wallet_ro USING (true);
    END IF;
END
$$;

COMMIT;
