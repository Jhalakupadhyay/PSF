package com.grobird.psf.video.dashboard;

import java.util.Map;

/**
 * Extracts the six dashboard metrics from a submission's fullResult (pitch-analyzer JSON).
 * Reads top-level voice_analysis, pose_analysis, facial_analysis, content_analysis.
 */
public final class DashboardMetricsExtractor {

    private static final double WPM_MIN = 40.0;
    private static final double WPM_MAX = 120.0;

    private DashboardMetricsExtractor() {}

    /**
     * Extract the six metrics from fullResult. Returns nulls for missing or non-numeric values.
     */
    public static DashboardMetricsSnapshot extract(Map<String, Object> fullResult) {
        if (fullResult == null) {
            return new DashboardMetricsSnapshot(null, null, null, null, null, null);
        }
        Map<String, Object> voice = getMap(fullResult, "voice_analysis");
        Map<String, Object> pose = getMap(fullResult, "pose_analysis");
        Map<String, Object> facial = getMap(fullResult, "facial_analysis");
        Map<String, Object> content = getMap(fullResult, "content_analysis");

        Double vocalDelivery = getDouble(voice, "overall_score");
        Double confidenceIndex = getDouble(pose, "overall_score");
        Double facialEngagement = getDouble(facial, "overall_score");
        Double contentQuality = getDouble(content, "overall_score");
        Double audienceEngagement = getDouble(facial, "engagement_score");
        Double speechFluency = computeSpeechFluency(voice, content);

        return new DashboardMetricsSnapshot(
                vocalDelivery,
                confidenceIndex,
                facialEngagement,
                contentQuality,
                speechFluency,
                audienceEngagement
        );
    }

    private static Double computeSpeechFluency(Map<String, Object> voice, Map<String, Object> content) {
        Double clarity = voice != null ? getDouble(voice, "clarity_score") : null;
        Double paceScore = voice != null ? getDouble(voice, "pace_score") : null;
        Double wpm = voice != null ? getDouble(voice, "speaking_rate_wpm") : null;
        Integer fillerCount = content != null ? getInteger(content, "filler_word_count") : null;

        double fillerComponent = 100.0;
        if (fillerCount != null) {
            fillerComponent = Math.max(0, 100 - fillerCount * 2);
        }

        double paceComponent;
        if (paceScore != null) {
            paceComponent = Math.max(0, Math.min(100, paceScore));
        } else if (wpm != null) {
            paceComponent = (wpm - WPM_MIN) / (WPM_MAX - WPM_MIN) * 100.0;
            paceComponent = Math.max(0, Math.min(100, paceComponent));
        } else {
            if (clarity == null) {
                return null;
            }
            return (clarity + fillerComponent) / 2.0;
        }

        if (clarity == null) {
            return (paceComponent + fillerComponent) / 2.0;
        }
        return (clarity + paceComponent + fillerComponent) / 3.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    private static Double getDouble(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return null;
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }
}
