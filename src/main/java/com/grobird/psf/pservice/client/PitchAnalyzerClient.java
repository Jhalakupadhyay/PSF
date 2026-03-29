package com.grobird.psf.pservice.client;

import com.grobird.psf.config.pservice.PServiceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client for the pitch-analyzer (Python) backend.
 * Upload video, create golden pitch deck (set_as_active: false), run analysis, poll status, get result.
 */
@Component
public class PitchAnalyzerClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final PServiceProperties properties;

    public PitchAnalyzerClient(@Qualifier("pServiceRestTemplate") RestTemplate restTemplate,
                              PServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * POST /api/v1/videos/upload — multipart form with "file". Returns map with "id" (video UUID).
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header)
     */
    public Map<String, Object> uploadVideo(Resource fileResource, String filename, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/videos/upload";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer upload");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * POST /api/v1/golden-pitch-decks — create reference (set_as_active: false). Returns map with "id", "is_processed", etc.
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header and user_id body field)
     */
    public Map<String, Object> createGoldenPitchDeck(String videoId, String name, String description, boolean setAsActive, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/golden-pitch-decks";
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("video_id", videoId);
        body.put("name", name != null ? name : "Reference");
        if (description != null) {
            body.put("description", description);
        }
        body.put("set_as_active", setAsActive);
        if (tenantId != null) {
            body.put("user_id", tenantId);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer create golden-pitch-deck");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer create golden-pitch-deck failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET /api/v1/golden-pitch-decks/{id} — poll until is_processed.
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header)
     */
    public Map<String, Object> getGoldenPitchDeck(String deckId, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/golden-pitch-decks/" + deckId;
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        HttpEntity<Void> entity = tenantId != null ? new HttpEntity<>(headers) : null;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer get golden-pitch-deck");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer get golden-pitch-deck failed: " + e.getMessage(), e);
        }
    }

    /**
     * POST /api/v1/analyses — start analysis with golden_pitch_deck_id. Returns map with "id", "status", etc.
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header and user_id body field)
     */
    public Map<String, Object> createAnalysis(String videoId, String goldenPitchDeckId, boolean skipComparison, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/analyses";
        if (!skipComparison && (goldenPitchDeckId == null || goldenPitchDeckId.isBlank())) {
            throw new PitchAnalyzerClientException("Comparison requested but golden_pitch_deck_id is null or blank");
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("video_id", videoId);
        if (goldenPitchDeckId != null) {
            body.put("golden_pitch_deck_id", goldenPitchDeckId);
        }
        body.put("skip_comparison", skipComparison);
        if (tenantId != null) {
            body.put("user_id", tenantId);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer create analysis");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer create analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET /api/v1/analyses/{id}/status — poll analysis progress.
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header)
     */
    public Map<String, Object> getAnalysisStatus(String analysisId, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/analyses/" + analysisId + "/status";
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        HttpEntity<Void> entity = tenantId != null ? new HttpEntity<>(headers) : null;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer get analysis status");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer get analysis status failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET /api/v1/analyses/{id} — full analysis result (report, comparison, etc.).
     * @param tenantId optional tenant ID for multi-tenant isolation (sent as X-User-Id header)
     */
    public Map<String, Object> getAnalysis(String analysisId, String tenantId) {
        String url = properties.getBaseUrl() + "/api/v1/analyses/" + analysisId;
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set("X-User-Id", tenantId);
        }
        HttpEntity<Void> entity = tenantId != null ? new HttpEntity<>(headers) : null;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new PitchAnalyzerClientException("Empty response from pitch-analyzer get analysis");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new PitchAnalyzerClientException("pitch-analyzer get analysis failed: " + e.getMessage(), e);
        }
    }
}
