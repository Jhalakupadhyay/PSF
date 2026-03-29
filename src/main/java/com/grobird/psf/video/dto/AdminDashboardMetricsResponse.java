package com.grobird.psf.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardMetricsResponse {

    private Double teamMatchScore;
    private Integer salesUserCount;
}
