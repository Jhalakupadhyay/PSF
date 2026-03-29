package com.grobird.psf.opportunity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminOpportunityResponse {

    private Long id;
    private String salesUserName;
    private String company;
    private String industry;
    private Instant createdAt;
    private Integer step;
    private boolean questionsGenerated;
}
