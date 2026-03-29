package com.grobird.psf.opportunity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create an opportunity. Only SALES can create; opportunity is tied to the current user (sales).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OpportunityRequest {

    @NotBlank(message = "Industry is required")
    private String industry;

    @NotBlank(message = "Company is required")
    private String company;
}
