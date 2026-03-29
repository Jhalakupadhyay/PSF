-- P Section: reference videos (admin) and sales submissions (sales), aligned with pitch-analyzer API.
-- reference_videos: unified table for golden pitch + skillset references (type GOLDEN_PITCH | SKILLSET).
-- sales_submissions: one row per sales video submission, linked to a reference for comparison.

-- Reference type enum (stored as VARCHAR for compatibility)
-- 'GOLDEN_PITCH' = one per tenant; 'SKILLSET' = many per tenant

CREATE TABLE IF NOT EXISTS reference_videos (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255),
    video_s3_key VARCHAR(1024),
    analyzer_video_id VARCHAR(36),
    analyzer_deck_id VARCHAR(36),
    is_processed BOOLEAN DEFAULT FALSE,
    created_by_user_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reference_videos_type CHECK (type IN ('GOLDEN_PITCH', 'SKILLSET')),
    CONSTRAINT fk_reference_videos_tenant FOREIGN KEY (tenant_id) REFERENCES organizations(id),
    CONSTRAINT fk_reference_videos_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_reference_videos_tenant_golden
    ON reference_videos (tenant_id) WHERE type = 'GOLDEN_PITCH';
CREATE INDEX IF NOT EXISTS idx_reference_videos_tenant_id ON reference_videos (tenant_id);
CREATE INDEX IF NOT EXISTS idx_reference_videos_type ON reference_videos (tenant_id, type);

COMMENT ON TABLE reference_videos IS 'Admin reference videos: golden pitch (one per tenant) and skillsets (many per tenant). Maps to pitch-analyzer via analyzer_video_id, analyzer_deck_id.';

-- Sales submissions: one per (opportunity, reference) - sales video compared against a reference
CREATE TABLE IF NOT EXISTS sales_submissions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    opportunity_id BIGINT NOT NULL,
    reference_video_id BIGINT NOT NULL,
    video_s3_key VARCHAR(1024) NOT NULL,
    analyzer_video_id VARCHAR(36),
    analyzer_analysis_id VARCHAR(36),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    overall_score NUMERIC(5, 2),
    comparison_score NUMERIC(5, 2),
    full_result JSONB,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_submissions_status CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    CONSTRAINT fk_sales_submissions_tenant FOREIGN KEY (tenant_id) REFERENCES organizations(id),
    CONSTRAINT fk_sales_submissions_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunities(id),
    CONSTRAINT fk_sales_submissions_reference FOREIGN KEY (reference_video_id) REFERENCES reference_videos(id)
);

CREATE INDEX IF NOT EXISTS idx_sales_submissions_tenant_id ON sales_submissions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_sales_submissions_opportunity_id ON sales_submissions (opportunity_id);
CREATE INDEX IF NOT EXISTS idx_sales_submissions_reference_video_id ON sales_submissions (reference_video_id);
CREATE INDEX IF NOT EXISTS idx_sales_submissions_analyzer_analysis_id ON sales_submissions (analyzer_analysis_id);

COMMENT ON TABLE sales_submissions IS 'Sales video submissions: one per upload, linked to a reference_video for comparison. Full result from pitch-analyzer stored in full_result.';

-- Migrate existing golden_pitch_deck data into reference_videos (if table exists and has rows)
INSERT INTO reference_videos (tenant_id, type, name, video_s3_key, created_at, updated_at)
SELECT tenant_id, 'GOLDEN_PITCH', 'Golden Pitch Deck', video_s3_key, updated_at, updated_at
FROM golden_pitch_deck
WHERE NOT EXISTS (
    SELECT 1 FROM reference_videos rv
    WHERE rv.tenant_id = golden_pitch_deck.tenant_id AND rv.type = 'GOLDEN_PITCH'
);

-- Migrate existing skillsets into reference_videos
INSERT INTO reference_videos (tenant_id, type, name, video_s3_key, created_at, updated_at)
SELECT tenant_id, 'SKILLSET', name, video_s3_key, created_at, updated_at
FROM skillsets;
