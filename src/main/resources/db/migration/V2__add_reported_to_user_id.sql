-- Multi-tenancy hierarchy: each tenant has admins; each admin has sales.
-- SALES users report to one ADMIN (same tenant).
ALTER TABLE users
ADD COLUMN IF NOT EXISTS reported_to_user_id BIGINT NULL;

COMMENT ON COLUMN users.reported_to_user_id IS 'For SALES: user id of the ADMIN they report to. NULL for ADMIN/SUPER_ADMIN.';

-- Optional: index for "list my sales" queries
CREATE INDEX IF NOT EXISTS idx_users_reported_to_user_id ON users (reported_to_user_id);
