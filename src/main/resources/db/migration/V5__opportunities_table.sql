-- Opportunities: each tied to one sales user; many opportunities per sales.
-- Sales can create; Admin can only delete (opportunities belonging to their sales).
CREATE TABLE IF NOT EXISTS opportunities (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    industry VARCHAR(255) NOT NULL,
    company VARCHAR(255) NOT NULL,
    sales_user_id BIGINT NOT NULL,
    CONSTRAINT fk_opportunities_sales FOREIGN KEY (sales_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_opportunities_tenant_id ON opportunities (tenant_id);
CREATE INDEX IF NOT EXISTS idx_opportunities_sales_user_id ON opportunities (sales_user_id);

COMMENT ON TABLE opportunities IS 'Each opportunity is tied to one sales user; sales create, admin can delete only.';
