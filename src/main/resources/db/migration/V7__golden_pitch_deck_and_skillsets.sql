-- Golden pitch deck: one per tenant; single video (S3 key).
CREATE TABLE IF NOT EXISTS golden_pitch_deck (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    video_s3_key VARCHAR(1024),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_golden_pitch_deck_tenant_id ON golden_pitch_deck (tenant_id);
COMMENT ON TABLE golden_pitch_deck IS 'One row per tenant; tenant-wide golden pitch deck video.';

-- Skillsets: many per tenant; each has name and optional video (S3 key).
CREATE TABLE IF NOT EXISTS skillsets (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    video_s3_key VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_skillsets_tenant_id ON skillsets (tenant_id);
COMMENT ON TABLE skillsets IS 'Tenant-wide skillsets; each has a name and optional video.';
