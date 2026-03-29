package com.grobird.psf.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardMetricsResponse {

    private Double vocalDelivery;
    private Double confidenceIndex;
    private Double facialEngagement;
    private Double contentQuality;
    private Double speechFluency;
    private Double audienceEngagement;
    private Integer submissionCount;
    private Double averageScore;
}
