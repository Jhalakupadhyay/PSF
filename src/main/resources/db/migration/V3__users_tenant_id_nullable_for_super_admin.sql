-- SUPER_ADMIN is the only user with no tenant (single global user, created manually).
-- All other users (ADMIN, SALES) must have tenant_id set.
ALTER TABLE users
ALTER COLUMN tenant_id DROP NOT NULL;

COMMENT ON COLUMN users.tenant_id IS 'NULL only for SUPER_ADMIN; required for ADMIN and SALES.';
