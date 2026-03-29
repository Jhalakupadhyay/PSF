-- Organizations (tenants) — created by Super Admin; referenced by users and opportunities as tenant_id
CREATE TABLE IF NOT EXISTS organizations (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    industry VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Track last login for "active organization" (admin active in last 7 days)
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

COMMENT ON TABLE organizations IS 'Tenants (organizations); each user and opportunity belongs to one via tenant_id.';
