package com.grobird.psf.opportunity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpportunityProgressResponse {

    private Long opportunityId;
    private String salesUserName;
    private String company;
    private String industry;
    private boolean questionsGenerated;
    private GoldenPitchProgress goldenPitch;
    private List<SkillsetProgressItem> skillsets;
}
