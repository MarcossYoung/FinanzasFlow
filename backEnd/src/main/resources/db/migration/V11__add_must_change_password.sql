-- Add must_change_password flag to usuarios.
-- Existing users default to false (no forced reset).
-- New users created by admin paths will be set to true at the application layer.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;
