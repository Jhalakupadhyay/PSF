package com.grobird.psf.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesSubmissionStatusResponse {

    private Long id;
    private String status;
    private BigDecimal overallScore;
    private BigDecimal comparisonScore;
    private Map<String, Object> fullResult;
    private String errorMessage;
    private String videoPlaybackUrl;
}
