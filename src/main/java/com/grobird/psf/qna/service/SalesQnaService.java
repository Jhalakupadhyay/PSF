package com.grobird.psf.qna.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.organization.entity.OrganizationEntity;
import com.grobird.psf.organization.repository.OrganizationRepository;
import com.grobird.psf.qna.cache.QnaCacheStore;
import com.grobird.psf.qna.client.QnaInfoClient;
import com.grobird.psf.qna.entity.QnaInfoEntity;
import com.grobird.psf.qna.entity.QnaQuestionsEntity;
import com.grobird.psf.qna.repository.QnaInfoRepository;
import com.grobird.psf.qna.repository.QnaQuestionsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Mediator service with 3-layer lookup: Redis (48h) → DB → qna_info API.
 * All data is mapped to an opportunityId.
 * <p>
 * Questions are paginated: each request returns up to PAGE_SIZE (10) questions
 * starting from the given offset. When the offset exceeds stored questions,
 * a fresh API call is made to generate more, which are appended and returned.
 */
@Service
public class SalesQnaService {

    private static final Logger log = LoggerFactory.getLogger(SalesQnaService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int PAGE_SIZE = 10;

    private final OrganizationRepository organizationRepository;
    private final OpportunityRepository opportunityRepository;
    private final QnaInfoClient qnaInfoClient;
    private final QnaCacheStore cacheStore;
    private final QnaQuestionsRepository questionsRepository;
    private final QnaInfoRepository infoRepository;
    private final ObjectMapper objectMapper;

    public SalesQnaService(OrganizationRepository organizationRepository,
                           OpportunityRepository opportunityRepository,
                           QnaInfoClient qnaInfoClient,
                           QnaCacheStore cacheStore,
                           QnaQuestionsRepository questionsRepository,
                           QnaInfoRepository infoRepository,
                           ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.opportunityRepository = opportunityRepository;
        this.qnaInfoClient = qnaInfoClient;
        this.cacheStore = cacheStore;
        this.questionsRepository = questionsRepository;
        this.infoRepository = infoRepository;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════
    //  Questions — paginated (both GET and GENERATE use the same flow)
    // ══════════════════════════════════════════════════════════════

    /**
     * Get questions for an opportunity (offset-based, 10 per page).
     * <ol>
     *   <li>Redis → if questions exist, slice [offset .. offset+10].</li>
     *   <li>DB → merge all batches, cache, slice [offset .. offset+10].</li>
     *   <li>If offset ≥ total stored (or nothing stored), call qna_info API
     *       to generate a new batch, append, cache, return the new page.</li>
     * </ol>
     */
    @Transactional
    public Map<String, Object> getQuestions(Long opportunityId, String targetCompanyOverride,
                                            int offset, UserPrincipal principal) {
        String sourceCompany = resolveSourceCompany(principal);
        String targetCompany = resolveTargetCompany(opportunityId, targetCompanyOverride);

        // 1. Redis
        Optional<String> cached = cacheStore.getQuestions(opportunityId);
        if (cached.isPresent()) {
            Map<String, Object> all = parseJson(cached.get());
            int total = totalQuestions(all);
            if (offset < total) {
                log.debug("Questions Redis HIT for opportunity {}, offset={}, total={}", opportunityId, offset, total);
                return slicePage(all, offset);
            }
            // offset beyond cached → fall through to generate more
        }

        // 2. DB
        List<QnaQuestionsEntity> saved = questionsRepository.findByOpportunityIdOrderByCreatedAtDesc(opportunityId);
        if (!saved.isEmpty()) {
            Map<String, Object> merged = mergeQuestionBatches(saved);
            cacheStore.putQuestions(opportunityId, toJson(merged));
            int total = totalQuestions(merged);
            if (offset < total) {
                log.debug("Questions DB HIT for opportunity {}, offset={}, total={}", opportunityId, offset, total);
                return slicePage(merged, offset);
            }
            // offset beyond DB → fall through to generate more
        }

        // 3. API call — generate new batch, append, return page
        log.debug("Questions need more for opportunity {}, offset={}, calling qna_info API", opportunityId, offset);
        return generateAndAppend(opportunityId, sourceCompany, targetCompany, offset);
    }

    /**
     * "Generate more questions" — same 3-layer check with offset.
     * Identical to getQuestions (both honour offset pagination).
     */
    @Transactional
    public Map<String, Object> generateQuestions(Long opportunityId, String targetCompanyOverride,
                                                 int offset, UserPrincipal principal) {
        return getQuestions(opportunityId, targetCompanyOverride, offset, principal);
    }

    // ── generate, save, merge, return page ──────────────────────

    private Map<String, Object> generateAndAppend(Long opportunityId, String sourceCompany,
                                                  String targetCompany, int offset) {
        Map<String, Object> apiResult = qnaInfoClient.postQuestionsGenerate(sourceCompany, targetCompany);

        // Save new batch in DB
        questionsRepository.save(QnaQuestionsEntity.builder()
                .opportunityId(opportunityId)
                .questionsJson(toJson(apiResult))
                .build());

        // Rebuild full merged set from all batches
        List<QnaQuestionsEntity> allBatches = questionsRepository.findByOpportunityIdOrderByCreatedAtDesc(opportunityId);
        Map<String, Object> merged = mergeQuestionBatches(allBatches);

        // Update Redis cache (full set)
        cacheStore.putQuestions(opportunityId, toJson(merged));

        return slicePage(merged, offset);
    }

    // ══════════════════════════════════════════════════════════════
    //  Info — unchanged (Redis → DB → API, no pagination)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> getCompanyInfo(Long opportunityId, String targetCompanyOverride,
                                              UserPrincipal principal) {
        String sourceCompany = resolveSourceCompany(principal);
        String targetCompany = resolveTargetCompany(opportunityId, targetCompanyOverride);

        // 1. Redis
        Optional<String> cached = cacheStore.getInfo(opportunityId);
        if (cached.isPresent()) {
            log.debug("Info cache HIT for opportunity {}", opportunityId);
            return parseJson(cached.get());
        }

        // 2. DB
        Optional<QnaInfoEntity> saved = infoRepository.findByOpportunityId(opportunityId);
        if (saved.isPresent()) {
            log.debug("Info DB HIT for opportunity {}", opportunityId);
            Map<String, Object> data = parseJson(saved.get().getInfoJson());
            cacheStore.putInfo(opportunityId, saved.get().getInfoJson());
            return data;
        }

        // 3. API call
        log.debug("Info MISS for opportunity {}, calling qna_info API", opportunityId);
        Map<String, Object> apiResult = qnaInfoClient.postCompanyInfo(sourceCompany, targetCompany);
        String json = toJson(apiResult);

        infoRepository.save(QnaInfoEntity.builder()
                .opportunityId(opportunityId)
                .infoJson(json)
                .build());
        cacheStore.putInfo(opportunityId, json);

        return apiResult;
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private String resolveSourceCompany(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Long tenantId = principal.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only users with an organization (Sales/Admin) can use this feature.");
        }
        OrganizationEntity org = organizationRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        String companyName = org.getCompanyName();
        if (companyName == null || companyName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization has no company name configured.");
        }
        return companyName.trim();
    }

    private String resolveTargetCompany(Long opportunityId, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        OpportunityEntity opp = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Opportunity not found: " + opportunityId));
        return opp.getCompany();
    }

    // ── Pagination ──────────────────────────────────────────────

    /**
     * Build a combined list (context_questions + ai_questions), then return
     * questions[offset .. offset+PAGE_SIZE] keeping the two lists separate.
     * Also includes "offset", "pageSize", "totalQuestions", and "hasMore" metadata.
     */
    private Map<String, Object> slicePage(Map<String, Object> data, int offset) {
        List<String> context = listOfStrings(data.get("context_questions"));
        List<String> ai = listOfStrings(data.get("ai_questions"));
        List<String> combined = new ArrayList<>(context);
        combined.addAll(ai);

        int total = combined.size();
        int safeOffset = Math.max(0, Math.min(offset, total));
        int end = Math.min(safeOffset + PAGE_SIZE, total);
        List<String> page = combined.subList(safeOffset, end);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source_company", data.get("source_company"));
        out.put("target_company", data.get("target_company"));
        out.put("questions", page);
        out.put("offset", safeOffset);
        out.put("pageSize", PAGE_SIZE);
        out.put("totalQuestions", total);
        out.put("hasMore", end < total);
        return out;
    }

    private int totalQuestions(Map<String, Object> data) {
        return listOfStrings(data.get("context_questions")).size()
                + listOfStrings(data.get("ai_questions")).size();
    }

    // ── Merge batches ───────────────────────────────────────────

    private Map<String, Object> mergeQuestionBatches(List<QnaQuestionsEntity> batches) {
        LinkedHashSet<String> contextQuestions = new LinkedHashSet<>();
        LinkedHashSet<String> aiQuestions = new LinkedHashSet<>();
        String sourceCompany = null;
        String targetCompany = null;

        for (QnaQuestionsEntity batch : batches) {
            Map<String, Object> data = parseJson(batch.getQuestionsJson());
            if (sourceCompany == null && data.get("source_company") != null) {
                sourceCompany = data.get("source_company").toString();
            }
            if (targetCompany == null && data.get("target_company") != null) {
                targetCompany = data.get("target_company").toString();
            }
            Object cq = data.get("context_questions");
            if (cq instanceof List<?> list) {
                list.forEach(q -> contextQuestions.add(q.toString()));
            }
            Object aq = data.get("ai_questions");
            if (aq instanceof List<?> list) {
                list.forEach(q -> aiQuestions.add(q.toString()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_company", sourceCompany);
        result.put("target_company", targetCompany);
        result.put("context_questions", new ArrayList<>(contextQuestions));
        result.put("ai_questions", new ArrayList<>(aiQuestions));
        return result;
    }

    // ── JSON ────────────────────────────────────────────────────

    private static List<String> listOfStrings(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse stored JSON", e);
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
