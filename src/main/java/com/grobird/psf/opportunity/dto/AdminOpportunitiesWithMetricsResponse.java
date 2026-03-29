package com.grobird.psf.opportunity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminOpportunitiesWithMetricsResponse {

    /** Populated only when request includes salesUserId filter. */
    private SalesUserMetricsResponse salesUserMetrics;

    private List<AdminOpportunityResponse> opportunities;
}
