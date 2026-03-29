-- Add target_user_id to notifications to support admin notifications.
-- target_user_id identifies who receives the notification (can be sales user or admin).
-- sales_user_id still identifies the sales user whose submission completed.

ALTER TABLE notifications ADD COLUMN target_user_id BIGINT;

-- Backfill existing rows: target_user_id = sales_user_id for existing sales notifications
UPDATE notifications SET target_user_id = sales_user_id WHERE target_user_id IS NULL;

-- Make target_user_id NOT NULL after backfill
ALTER TABLE notifications ALTER COLUMN target_user_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_target_user FOREIGN KEY (target_user_id) REFERENCES users(id);

-- Add index for querying notifications by target user
CREATE INDEX IF NOT EXISTS idx_notifications_target_user ON notifications (target_user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_target_user_read_at ON notifications (target_user_id, read_at);

COMMENT ON COLUMN notifications.target_user_id IS 'The user who receives this notification (sales user or admin)';
COMMENT ON COLUMN notifications.sales_user_id IS 'The sales user whose submission completed (for context in admin notifications)';
