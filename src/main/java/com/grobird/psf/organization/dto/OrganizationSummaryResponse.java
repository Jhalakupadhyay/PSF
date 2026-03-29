package com.grobird.psf.organization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationSummaryResponse {

    private Long id;
    private String companyName;
    private String industry;
    private Instant createdAt;
    private Long totalAdmins;
    private Long totalOpportunities;
}
