-- Add invitation_status column to track sales user invitation state.
-- PENDING = email sent with credentials, awaiting first login
-- ACCEPTED = user has logged in at least once

ALTER TABLE users ADD COLUMN invitation_status VARCHAR(20);

-- Backfill existing SALES users as ACCEPTED (they already have accounts)
UPDATE users SET invitation_status = 'ACCEPTED' WHERE role = 'SALES';

CREATE INDEX IF NOT EXISTS idx_users_invitation_status ON users (invitation_status);

COMMENT ON COLUMN users.invitation_status IS 'For SALES users: PENDING until first login, then ACCEPTED';
