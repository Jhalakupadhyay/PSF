-- Add created_at timestamp to opportunities table for admin dashboard.

ALTER TABLE opportunities ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Backfill existing rows with current timestamp
UPDATE opportunities SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

-- Make NOT NULL after backfill
ALTER TABLE opportunities ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_opportunities_created_at ON opportunities (created_at DESC);

COMMENT ON COLUMN opportunities.created_at IS 'When the opportunity was created';
