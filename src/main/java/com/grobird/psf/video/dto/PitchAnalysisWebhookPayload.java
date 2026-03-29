package com.grobird.psf.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PitchAnalysisWebhookPayload {

    @JsonProperty("analysis_id")
    private String analysisId;
    private String status;
    private Double overall_score;
    private Double comparison_score;
    private String error_message;
}
