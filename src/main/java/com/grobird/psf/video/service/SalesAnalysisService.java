package com.grobird.psf.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.pservice.client.PitchAnalyzerClient;
import com.grobird.psf.pservice.client.PitchAnalyzerClientException;
import com.grobird.psf.video.cache.PSectionCacheStore;
import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.ReferenceVideoRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Handles the heavy, long-running analysis workflow for sales submissions.
 * This service is responsible for calling the pitch-analyzer backend and updating submissions.
 */
@Service
@Transactional
public class SalesAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SalesAnalysisService.class);
    private static final int POLL_INTERVAL_SEC = 5;
    private static final int MAX_POLL_ATTEMPTS = 360; // 30 min at 5s
    private static final int REFERENCE_KEY_RETRY_INTERVAL_SEC = 2;
    private static final int REFERENCE_KEY_MAX_RETRIES = 15; // 30s total

    private final SalesSubmissionRepository submissionRepository;
    private final ReferenceVideoRepository referenceVideoRepository;
    private final S3VideoDownloadService s3VideoDownloadService;
    private final PitchAnalyzerClient pitchAnalyzerClient;
    private final ObjectMapper objectMapper;
    private final PSectionCacheStore pSectionCacheStore;
    private final SalesDashboardMetricsService salesDashboardMetricsService;

    public SalesAnalysisService(SalesSubmissionRepository submissionRepository,
                                ReferenceVideoRepository referenceVideoRepository,
                                S3VideoDownloadService s3VideoDownloadService,
                                PitchAnalyzerClient pitchAnalyzerClient,
                                ObjectMapper objectMapper,
                                PSectionCacheStore pSectionCacheStore,
                                SalesDashboardMetricsService salesDashboardMetricsService) {
        this.submissionRepository = submissionRepository;
        this.referenceVideoRepository = referenceVideoRepository;
        this.s3VideoDownloadService = s3VideoDownloadService;
        this.pitchAnalyzerClient = pitchAnalyzerClient;
        this.objectMapper = objectMapper;
        this.pSectionCacheStore = pSectionCacheStore;
        this.salesDashboardMetricsService = salesDashboardMetricsService;
    }

    /**
     * Async transactional entrypoint: set tenant context, run analysis, and handle failures.
     */
    @Async
    public void runAnalysisAsync(Long submissionId, Long tenantId) {
        if (tenantId == null) {
            log.error("Submission {} has null tenant id, skipping analysis", submissionId);
            return;
        }
        try {
            TenantContext.setTenantId(tenantId);
            runAnalysis(submissionId);
        } catch (Exception e) {
            log.error("Analysis failed for submission {}", submissionId, e);
            submissionRepository.findById(submissionId).ifPresent(s -> {
                s.setStatus(SalesSubmissionStatus.failed);
                s.setErrorMessage(e.getMessage());
                s.setUpdatedAt(Instant.now());
                submissionRepository.save(s);
            });
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Register reference with pitch-analyzer if not already (upload, create golden deck, poll until processed).
     * Updates reference entity with analyzer_video_id, analyzer_deck_id, is_processed.
     */
    private void ensureReferenceRegistered(ReferenceVideoEntity reference) {
        if (reference.getVideoS3Key() == null || reference.getVideoS3Key().isBlank()) {
            throw new IllegalStateException("Reference video has no S3 key");
        }
        if (reference.getAnalyzerDeckId() != null && reference.isProcessed()) {
            return;
        }
        String tenantId = reference.getTenantId() != null ? String.valueOf(reference.getTenantId()) : null;
        org.springframework.core.io.Resource refResource = s3VideoDownloadService.getResource(reference.getVideoS3Key());
        Map<String, Object> uploadResp = pitchAnalyzerClient.uploadVideo(refResource, refResource.getFilename(), tenantId);
        String analyzerVideoId = (String) uploadResp.get("id");
        if (analyzerVideoId == null) {
            throw new PitchAnalyzerClientException("Upload response missing id");
        }
        Map<String, Object> deckResp = pitchAnalyzerClient.createGoldenPitchDeck(
                analyzerVideoId,
                reference.getName() != null ? reference.getName() : "Reference",
                null,
                false,
                tenantId
        );
        String analyzerDeckId = (String) deckResp.get("id");
        if (analyzerDeckId == null) {
            throw new PitchAnalyzerClientException("Create golden-pitch-deck response missing id");
        }
        reference.setAnalyzerVideoId(analyzerVideoId);
        reference.setAnalyzerDeckId(analyzerDeckId);
        reference.setUpdatedAt(Instant.now());
        referenceVideoRepository.save(reference);

        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            try {
                TimeUnit.SECONDS.sleep(POLL_INTERVAL_SEC);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for reference processing", e);
            }
            Map<String, Object> deck = pitchAnalyzerClient.getGoldenPitchDeck(analyzerDeckId, tenantId);
            Boolean processed = (Boolean) deck.get("is_processed");
            if (Boolean.TRUE.equals(processed)) {
                reference.setProcessed(true);
                reference.setUpdatedAt(Instant.now());
                referenceVideoRepository.save(reference);
                return;
            }
        }
        throw new PitchAnalyzerClientException("Reference processing did not complete within timeout");
    }

    /**
     * Async entrypoint used by admin flows to eagerly register a reference with the pitch-analyzer.
     * This is fire-and-forget: failures are logged but not propagated to the caller.
     */
    @Async
    public void registerReferenceAsync(Long referenceId, Long tenantId) {
        if (tenantId == null) {
            log.error("registerReferenceAsync called with null tenantId for reference {}", referenceId);
            return;
        }
        try {
            TenantContext.setTenantId(tenantId);
            ReferenceVideoEntity reference = referenceVideoRepository.findById(referenceId)
                    .orElse(null);
            if (reference == null) {
                log.warn("registerReferenceAsync: reference {} not found", referenceId);
                return;
            }
            if (reference.getVideoS3Key() == null || reference.getVideoS3Key().isBlank()) {
                reference = waitForReferenceVideoKey(referenceId);
                if (reference == null) {
                    log.warn("registerReferenceAsync: reference {} has no S3 key yet after retry window", referenceId);
                    return;
                }
            }
            ensureReferenceRegistered(reference);
        } catch (Exception e) {
            log.error("Failed to eagerly register reference {} with pitch-analyzer", referenceId, e);
        } finally {
            TenantContext.clear();
        }
    }

    private ReferenceVideoEntity waitForReferenceVideoKey(Long referenceId) {
        for (int i = 0; i < REFERENCE_KEY_MAX_RETRIES; i++) {
            try {
                TimeUnit.SECONDS.sleep(REFERENCE_KEY_RETRY_INTERVAL_SEC);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            ReferenceVideoEntity candidate = referenceVideoRepository.findById(referenceId).orElse(null);
            if (candidate == null) {
                return null;
            }
            if (candidate.getVideoS3Key() != null && !candidate.getVideoS3Key().isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Run analysis job for a submission: ensure reference registered, upload sales video, POST /analyses, poll, save result.
     */
    private void runAnalysis(Long submissionId) {
        SalesSubmissionEntity submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        ReferenceVideoEntity reference = submission.getReferenceVideo();
        if (reference == null) {
            throw new IllegalStateException("Submission has no reference video");
        }

        submission.setStatus(SalesSubmissionStatus.processing);
        submission.setUpdatedAt(Instant.now());
        submissionRepository.save(submission);

        ensureReferenceRegistered(reference);
        reference = referenceVideoRepository.findById(reference.getId()).orElseThrow();

        // Hard requirement: we only support analysis + comparison.
        // If for any reason the reference is still missing a deck id, fail fast instead of running analysis-only.
        if (reference.getAnalyzerDeckId() == null || reference.getAnalyzerDeckId().isBlank()) {
            log.error("Reference {} is processed but analyzerDeckId is null/blank for submission {}",
                    reference.getId(), submissionId);
            submission.setStatus(SalesSubmissionStatus.failed);
            submission.setErrorMessage("Reference video is not ready for comparison. Please try again later.");
            submission.setUpdatedAt(Instant.now());
            submissionRepository.save(submission);
            return;
        }

        String tenantId = submission.getTenantId() != null ? String.valueOf(submission.getTenantId()) : null;
        org.springframework.core.io.Resource salesResource = s3VideoDownloadService.getResource(submission.getVideoS3Key());
        Map<String, Object> uploadResp = pitchAnalyzerClient.uploadVideo(salesResource, salesResource.getFilename(), tenantId);
        String analyzerVideoId = (String) uploadResp.get("id");
        if (analyzerVideoId == null) {
            throw new PitchAnalyzerClientException("Upload response missing id");
        }
        submission.setAnalyzerVideoId(analyzerVideoId);
        submissionRepository.save(submission);

        Map<String, Object> analysisResp = pitchAnalyzerClient.createAnalysis(
                analyzerVideoId,
                reference.getAnalyzerDeckId(),
                false,
                tenantId
        );
        String analyzerAnalysisId = (String) analysisResp.get("id");
        if (analyzerAnalysisId == null) {
            throw new PitchAnalyzerClientException("Create analysis response missing id");
        }
        submission.setAnalyzerAnalysisId(analyzerAnalysisId);
        submissionRepository.save(submission);

        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            try {
                TimeUnit.SECONDS.sleep(POLL_INTERVAL_SEC);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for analysis", e);
            }
            Map<String, Object> status = pitchAnalyzerClient.getAnalysisStatus(analyzerAnalysisId, tenantId);
            String statusStr = (String) status.get("status");
            if ("completed".equals(statusStr)) {
                Map<String, Object> fullResult = pitchAnalyzerClient.getAnalysis(analyzerAnalysisId, tenantId);
                submission.setFullResult(fullResult);
                boolean hasComparisonScore = false;
                Object report = fullResult.get("report");
                if (report instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reportMap = (Map<String, Object>) report;
                    Object overall = reportMap.get("overall_score");
                    Object comparison = reportMap.get("comparison_overall_score");
                    if (overall instanceof Number) {
                        submission.setOverallScore(BigDecimal.valueOf(((Number) overall).doubleValue()));
                    }
                    if (comparison instanceof Number) {
                        submission.setComparisonScore(BigDecimal.valueOf(((Number) comparison).doubleValue()));
                        hasComparisonScore = true;
                    }
                }
                // Non-negotiable: treat missing comparison score as a failure, not a successful completion.
                if (!hasComparisonScore) {
                    log.error("Analysis {} for submission {} completed without comparison score", analyzerAnalysisId, submissionId);
                    submission.setStatus(SalesSubmissionStatus.failed);
                    submission.setErrorMessage("Analysis completed but comparison score was not returned by analyzer.");
                } else {
                    submission.setStatus(SalesSubmissionStatus.completed);
                }
                submission.setUpdatedAt(Instant.now());
                submissionRepository.save(submission);
                if (submission.getStatus() == SalesSubmissionStatus.completed) {
                    putCompletedResultInCache(submission);
                    salesDashboardMetricsService.onSubmissionCompleted(submission);
                }
                return;
            }
            if ("failed".equals(statusStr)) {
                submission.setStatus(SalesSubmissionStatus.failed);
                submission.setErrorMessage((String) status.get("error_message"));
                submission.setUpdatedAt(Instant.now());
                submissionRepository.save(submission);
                return;
            }
        }
        submission.setStatus(SalesSubmissionStatus.failed);
        submission.setErrorMessage("Analysis did not complete within timeout");
        submission.setUpdatedAt(Instant.now());
        submissionRepository.save(submission);
    }

    /**
     * Handle webhook callback from pitch-analyzer: update submission status and optionally fetch full result.
     */
    public void handleWebhookCallback(String analyzerAnalysisId, String status, Double overallScore, Double comparisonScore, String errorMessage) {
        Optional<SalesSubmissionEntity> opt = submissionRepository.findByAnalyzerAnalysisId(analyzerAnalysisId);
        if (opt.isEmpty()) {
            log.warn("Webhook for unknown analysis_id: {}", analyzerAnalysisId);
            return;
        }
        SalesSubmissionEntity submission = opt.get();
        String tenantId = submission.getTenantId() != null ? String.valueOf(submission.getTenantId()) : null;
        if ("completed".equals(status)) {
            boolean hasComparisonScore = false;
            try {
                Map<String, Object> fullResult = pitchAnalyzerClient.getAnalysis(analyzerAnalysisId, tenantId);
                submission.setFullResult(fullResult);
                Object report = fullResult.get("report");
                if (report instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reportMap = (Map<String, Object>) report;
                    Object overall = reportMap.get("overall_score");
                    Object comparison = reportMap.get("comparison_overall_score");
                    if (overall instanceof Number) {
                        submission.setOverallScore(BigDecimal.valueOf(((Number) overall).doubleValue()));
                    }
                    if (comparison instanceof Number) {
                        submission.setComparisonScore(BigDecimal.valueOf(((Number) comparison).doubleValue()));
                        hasComparisonScore = true;
                    }
                }
                if (overallScore != null) {
                    submission.setOverallScore(BigDecimal.valueOf(overallScore));
                }
                if (comparisonScore != null) {
                    submission.setComparisonScore(BigDecimal.valueOf(comparisonScore));
                    hasComparisonScore = true;
                }
            } catch (Exception e) {
                log.error("Failed to fetch full result for analysis {}", analyzerAnalysisId, e);
            }
            if (!hasComparisonScore) {
                log.error("Webhook completed for analysis {} but comparison score is missing", analyzerAnalysisId);
                submission.setStatus(SalesSubmissionStatus.failed);
                submission.setErrorMessage(errorMessage != null
                        ? errorMessage
                        : "Analysis completed via webhook but comparison score was not returned by analyzer.");
            } else {
                submission.setStatus(SalesSubmissionStatus.completed);
            }
        } else if ("failed".equals(status) || "analysis_failed".equalsIgnoreCase(status)) {
            submission.setStatus(SalesSubmissionStatus.failed);
            submission.setErrorMessage(errorMessage != null ? errorMessage : "Analysis failed");
        }
        submission.setUpdatedAt(Instant.now());
        submissionRepository.save(submission);
        if ("completed".equals(status) && submission.getStatus() == SalesSubmissionStatus.completed) {
            putCompletedResultInCache(submission);
            salesDashboardMetricsService.onSubmissionCompleted(submission);
        }
    }

    private void putCompletedResultInCache(SalesSubmissionEntity submission) {
        if (pSectionCacheStore == null || submission.getFullResult() == null) {
            return;
        }
        ReferenceVideoEntity ref = submission.getReferenceVideo();
        if (ref == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(submission.getFullResult());
            if (ref.getType() == ReferenceVideoType.GOLDEN_PITCH) {
                pSectionCacheStore.putSalesGoldenResult(submission.getOpportunityId(), json);
            } else {
                pSectionCacheStore.putSalesSkillsetResult(submission.getOpportunityId(), ref.getId(), json);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache P section result for submission {}", submission.getId(), e);
        }
    }
}

