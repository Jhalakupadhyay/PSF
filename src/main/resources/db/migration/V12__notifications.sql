-- Sales notifications for completed analyses.
-- Only the sales owner of an opportunity should receive and manage these notifications.

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    sales_user_id BIGINT NOT NULL,
    opportunity_id BIGINT NOT NULL,
    sales_submission_id BIGINT NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_video_id BIGINT,
    reference_video_name VARCHAR(255),
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_reference_type CHECK (reference_type IN ('GOLDEN_PITCH', 'SKILLSET')),
    CONSTRAINT fk_notifications_tenant FOREIGN KEY (tenant_id) REFERENCES organizations(id),
    CONSTRAINT fk_notifications_sales_user FOREIGN KEY (sales_user_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunities(id),
    CONSTRAINT fk_notifications_submission FOREIGN KEY (sales_submission_id) REFERENCES sales_submissions(id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_sales_user ON notifications (sales_user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_sales_user_read_at ON notifications (sales_user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications (created_at DESC);

COMMENT ON TABLE notifications IS 'Sales notifications for completed analyses, scoped to the sales owner of the opportunity.';
