-- Sales-specific fields when admin adds a sales person (username used for name, no first_name/last_name columns)
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_number VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS department VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_id VARCHAR(255);

-- password remains NOT NULL; admin always sets initial password when creating a sales
