package com.grobird.psf.qna.client;

import com.grobird.psf.config.qna.QnaInfoProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for the qna_info FastAPI service. Used by the Spring mediator.
 */
@Component
public class QnaInfoClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<Map<String, String>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate qnaInfoRestTemplate;
    private final QnaInfoProperties properties;

    public QnaInfoClient(@Qualifier("qnaInfoRestTemplate") RestTemplate qnaInfoRestTemplate,
                         QnaInfoProperties properties) {
        this.qnaInfoRestTemplate = qnaInfoRestTemplate;
        this.properties = properties;
    }

    /**
     * POST /api/company/info with source_company and target_company.
     * Returns the response body as a map (pass-through for client).
     */
    public Map<String, Object> postCompanyInfo(String sourceCompany, String targetCompany) {
        String url = properties.getBaseUrl() + "/api/company/info";
        Map<String, String> body = Map.of(
                "source_company", sourceCompany,
                "target_company", targetCompany
        );
        return postJson(url, body);
    }

    /**
     * POST /api/company/questions/generate with source_company and target_company.
     * Returns the response body as a map (pass-through for client).
     */
    public Map<String, Object> postQuestionsGenerate(String sourceCompany, String targetCompany) {
        String url = properties.getBaseUrl() + "/api/company/questions/generate";
        Map<String, String> body = Map.of(
                "source_company", sourceCompany,
                "target_company", targetCompany
        );
        return postJson(url, body);
    }

    /**
     * GET /api/company/suggest?q={query} — autocomplete company names.
     * Returns list of suggestions each containing name, domain, and logo URL.
     */
    public List<Map<String, String>> suggestCompanies(String query) {
        String url = properties.getBaseUrl() + "/api/company/suggest?q=" + query;
        try {
            ResponseEntity<List<Map<String, String>>> response = qnaInfoRestTemplate.exchange(
                    url, HttpMethod.GET, null, LIST_MAP_TYPE);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            throw new QnaInfoClientException("Company suggestion request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> postJson(String url, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = qnaInfoRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MAP_TYPE
            );
            if (response.getBody() == null) {
                throw new QnaInfoClientException("Empty response from qna_info");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new QnaInfoClientException("qna_info service unavailable: " + e.getMessage(), e);
        }
    }
}
