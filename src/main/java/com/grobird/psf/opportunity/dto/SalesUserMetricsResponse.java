package com.grobird.psf.opportunity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesUserMetricsResponse {

    private Long salesUserId;
    private String salesUserName;
    private BigDecimal averageScore;
    private BigDecimal vocalDeliveryAvg;
    private BigDecimal confidenceIndexAvg;
    private BigDecimal facialEngagementAvg;
    private BigDecimal contentQualityAvg;
    private BigDecimal speechFluencyAvg;
    private BigDecimal audienceEngagementAvg;
    private Integer submissionCount;
}
