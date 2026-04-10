-- Identity should not enforce account EAN ownership.
-- EAN is sourced from Account Management and may be null in identity users.

ALTER TABLE users
    ALTER COLUMN ean DROP NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_ean'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT uk_users_ean;
    END IF;
END
$$;

DROP INDEX IF EXISTS uk_users_ean;
