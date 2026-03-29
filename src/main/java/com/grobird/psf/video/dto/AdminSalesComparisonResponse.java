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
public class AdminSalesComparisonResponse {

    private String type;
    private SalesUserMetrics selectedUser;
    private TeamAverageMetrics teamAverage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SalesUserMetrics {
        private Long userId;
        private String username;
        private Double vocalDelivery;
        private Double confidenceIndex;
        private Double facialEngagement;
        private Double contentQuality;
        private Double speechFluency;
        private Double audienceEngagement;
        private Double averageScore;
        private Integer submissionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TeamAverageMetrics {
        private Double vocalDelivery;
        private Double confidenceIndex;
        private Double facialEngagement;
        private Double contentQuality;
        private Double speechFluency;
        private Double audienceEngagement;
        private Double averageScore;
        private Integer salesUserCount;
        private Integer totalSubmissions;
    }
}
