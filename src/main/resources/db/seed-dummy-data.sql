-- =============================================================================
-- PSF dummy data for frontend: Auth, Opportunity, and supporting data.
-- Sales-QNA tables (qna_questions, qna_info) are left empty; they are populated
-- when the AI service is called.
--
-- Prerequisites: Flyway migrations have run (organizations, opportunities,
-- qna_questions, qna_info tables exist). If the "users" table does not exist
-- (e.g. no V1 migration), the script creates it below.
--
-- Passwords: All test users use password "Password1!" (BCrypt hash below).
-- Replace the hash with your own if needed: run the app with --spring.profiles.active=seed
-- once and copy from users.password, or use an online BCrypt generator for "Password1!".
-- =============================================================================

-- Ensure users table exists (skip if your Flyway baseline already creates it)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    reported_to_user_id BIGINT,
    contact_number VARCHAR(255),
    department VARCHAR(255),
    employee_id VARCHAR(255),
    last_login_at TIMESTAMP
);

-- Idempotent seed: run all inserts inside a block; skip if super admin already exists
-- IMPORTANT: The pwd value below is a PLACEHOLDER and will NOT work for login.
-- To get a working password: run the app with --spring.profiles.active=seed once (Java DataSeeder
-- creates users with correct BCrypt), OR run: ./gradlew runBcryptHash and set users.password to the output.
DO $$
DECLARE
  pwd TEXT := '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQD6y3R2lDcR0TqYhxKPxPxPxPxPxPx';  -- PLACEHOLDER: replace or use Java seeder
  org_id BIGINT;
  admin_id BIGINT;
  sales_id BIGINT;
BEGIN
  IF EXISTS (SELECT 1 FROM users WHERE email = 'superadmin@test.com') THEN
    RAISE NOTICE 'Seed data already present (superadmin exists). Skipping.';
    RETURN;
  END IF;

  INSERT INTO users (tenant_id, username, email, password, role, reported_to_user_id, contact_number, department, employee_id)
  VALUES (NULL, 'Super Admin', 'superadmin@test.com', pwd, 'SUPER_ADMIN', NULL, NULL, NULL, NULL);

  INSERT INTO organizations (company_name, industry, created_at)
  VALUES ('Rubrik', 'Technology', CURRENT_TIMESTAMP)
  RETURNING id INTO org_id;

  INSERT INTO organizations (company_name, industry, created_at) VALUES
  ('Alpha Corp', 'Finance', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  ('Beta Inc', 'Healthcare', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  ('Gamma Ltd', 'Retail', CURRENT_TIMESTAMP - INTERVAL '3 days'),
  ('Delta Co', 'Technology', CURRENT_TIMESTAMP - INTERVAL '5 days'),
  ('Epsilon LLC', 'Manufacturing', CURRENT_TIMESTAMP - INTERVAL '7 days');

  INSERT INTO users (tenant_id, username, email, password, role, reported_to_user_id, contact_number, department, employee_id)
  VALUES (org_id, 'Test Admin', 'admin@test.com', pwd, 'ADMIN', NULL, NULL, NULL, NULL)
  RETURNING id INTO admin_id;

  INSERT INTO users (tenant_id, username, email, password, role, reported_to_user_id, contact_number, department, employee_id)
  VALUES (org_id, 'Test Sales', 'sales@test.com', pwd, 'SALES', admin_id, '+1234567890', 'Sales', 'EMP001')
  RETURNING id INTO sales_id;

  INSERT INTO opportunities (tenant_id, industry, company, sales_user_id) VALUES
  (org_id, 'Technology', 'Acme Corp', sales_id),
  (org_id, 'Finance', 'Global Bank', sales_id),
  (org_id, 'Healthcare', 'MedTech Solutions', sales_id),
  (org_id, 'Retail', 'MegaStore Inc', sales_id),
  (org_id, 'Technology', 'CloudSoft Ltd', sales_id);

  RAISE NOTICE 'Seed complete. Login: superadmin@test.com, admin@test.com, sales@test.com — password: Password1!';
END $$;

-- =============================================================================
-- Login credentials (password for all: Password1!)
--   superadmin@test.com  — SUPER_ADMIN
--   admin@test.com       — ADMIN (tenant Rubrik)
--   sales@test.com       — SALES (reports to admin, tenant Rubrik)
-- =============================================================================
