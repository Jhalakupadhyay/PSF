-- Add average_score column to sales_dashboard_metrics.
-- Stores the normalized (0-5 scale) average of comparisonScore from all completed submissions.

ALTER TABLE sales_dashboard_metrics
    ADD COLUMN IF NOT EXISTS average_score NUMERIC(3, 2);

COMMENT ON COLUMN sales_dashboard_metrics.average_score IS 'Average comparison score normalized to 0-5 scale: (avg(comparisonScore) / 100) * 5';
