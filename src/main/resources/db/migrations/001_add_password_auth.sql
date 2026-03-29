-- Migration: add email/password authentication support
-- Run once: psql -U sasoori sasoori_db -f src/main/resources/db/migrations/001_add_password_auth.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Relax the identity constraint to allow email+password-only users
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_identity;
ALTER TABLE users ADD CONSTRAINT users_identity
    CHECK (google_sub IS NOT NULL OR phone IS NOT NULL OR password_hash IS NOT NULL);
