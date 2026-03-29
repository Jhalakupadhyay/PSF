package com.grobird.psf.video.dashboard;

import java.util.Map;

/**
 * Extracted dashboard metrics from a single submission's fullResult JSON.
 * Each field may be null if not present or non-numeric.
 */
public record DashboardMetricsSnapshot(
        Double vocalDelivery,
        Double confidenceIndex,
        Double facialEngagement,
        Double contentQuality,
        Double speechFluency,
        Double audienceEngagement
) {}
