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
public class SkillsetProgressItem {

    private String skillsetName;
    private Long referenceVideoId;
    private String status;
    private Double comparisonScore;
    private Instant submittedAt;
}
