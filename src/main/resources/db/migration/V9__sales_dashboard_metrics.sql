-- Per-sales-user aggregated dashboard metrics from completed pitch submissions.
-- One row per (tenant_id, user_id); updated when a new submission completes.

CREATE TABLE IF NOT EXISTS sales_dashboard_metrics (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vocal_delivery_avg NUMERIC(5, 2),
    confidence_index_avg NUMERIC(5, 2),
    facial_engagement_avg NUMERIC(5, 2),
    content_quality_avg NUMERIC(5, 2),
    speech_fluency_avg NUMERIC(5, 2),
    audience_engagement_avg NUMERIC(5, 2),
    submission_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sales_dashboard_metrics_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_sales_dashboard_metrics_tenant FOREIGN KEY (tenant_id) REFERENCES organizations(id),
    CONSTRAINT fk_sales_dashboard_metrics_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_sales_dashboard_metrics_tenant_user ON sales_dashboard_metrics (tenant_id, user_id);

COMMENT ON TABLE sales_dashboard_metrics IS 'Aggregated pitch metrics per sales user for dashboard; recomputed when a submission completes.';
