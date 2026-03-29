-- Per-admin aggregated team dashboard metrics.
-- One row per (tenant_id, admin_user_id); updated when any sales user's metrics change.

CREATE TABLE IF NOT EXISTS admin_dashboard_metrics (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    team_match_score NUMERIC(3, 2),
    sales_user_count INTEGER DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_admin_dashboard_metrics_tenant_admin UNIQUE (tenant_id, admin_user_id),
    CONSTRAINT fk_admin_dashboard_metrics_tenant FOREIGN KEY (tenant_id) REFERENCES organizations(id),
    CONSTRAINT fk_admin_dashboard_metrics_admin FOREIGN KEY (admin_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_admin_dashboard_metrics_tenant_admin ON admin_dashboard_metrics (tenant_id, admin_user_id);

COMMENT ON TABLE admin_dashboard_metrics IS 'Aggregated team metrics per admin user for dashboard; recomputed when any sales user metrics change.';
COMMENT ON COLUMN admin_dashboard_metrics.team_match_score IS 'Average of all sales users averageScore (0-5 scale) who report to this admin.';
