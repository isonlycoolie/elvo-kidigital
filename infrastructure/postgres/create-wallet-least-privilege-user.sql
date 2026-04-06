-- ELVO Wallet Service: least-privilege runtime role creation
-- Run as a PostgreSQL superuser/DBA in the target environment.
-- Required psql variables:
--   wallet_db_name
--   wallet_app_user
--   wallet_app_password
-- Example:
--   psql -v wallet_db_name=elvo_wallet -v wallet_app_user=elvo_wallet_app -v wallet_app_password='strong-secret' -f create-wallet-least-privilege-user.sql

\set ON_ERROR_STOP on

DO $$
DECLARE
    target_db text := :'wallet_db_name';
    app_user text := :'wallet_app_user';
    app_password text := :'wallet_app_password';
BEGIN
    IF target_db IS NULL OR target_db = '' THEN
        RAISE EXCEPTION 'wallet_db_name is required';
    END IF;

    IF app_user IS NULL OR app_user = '' THEN
        RAISE EXCEPTION 'wallet_app_user is required';
    END IF;

    IF app_password IS NULL OR app_password = '' THEN
        RAISE EXCEPTION 'wallet_app_password is required';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'wallet_app_user') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', :'wallet_app_user', :'wallet_app_password');
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'wallet_app_user', :'wallet_app_password');
    END IF;
END $$;

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'wallet_db_name', :'wallet_app_user') \gexec

\connect :wallet_db_name

SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'wallet_app_user') \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'wallet_app_user') \gexec
SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'wallet_app_user') \gexec

SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'wallet_app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', :'wallet_app_user') \gexec

SELECT format('REVOKE CREATE ON SCHEMA public FROM %I', :'wallet_app_user') \gexec
SELECT format('REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM %I', :'wallet_app_user') \gexec

-- Enforce append-only style on ledger entries for runtime role.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'ledger_entries'
    ) THEN
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.ledger_entries FROM %I', :'wallet_app_user');
    END IF;
END $$;
