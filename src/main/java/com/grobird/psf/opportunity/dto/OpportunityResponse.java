package com.grobird.psf.opportunity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpportunityResponse {

    private Long id;
    private String industry;
    private String company;
    private Long salesUserId;
    private String tenantId;
    /** 1 = Q step, 2 = P step, 3 = Final analysis */
    private Integer step;
}
