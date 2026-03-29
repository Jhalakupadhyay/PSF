-- Add created_at column to users table for tracking user creation date.

ALTER TABLE users ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Backfill existing users with current timestamp
UPDATE users SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at DESC);

COMMENT ON COLUMN users.created_at IS 'When the user account was created';
