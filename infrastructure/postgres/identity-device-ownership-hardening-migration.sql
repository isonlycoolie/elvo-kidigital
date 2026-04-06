-- ELVO Identity device ownership hardening migration
-- Safe to run multiple times.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_devices_device_id'
    ) THEN
        ALTER TABLE devices DROP CONSTRAINT uk_devices_device_id;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_devices_user_device_id'
    ) THEN
        ALTER TABLE devices
            ADD CONSTRAINT uk_devices_user_device_id UNIQUE (user_id, device_id);
    END IF;
END $$;
