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
public class LeaderboardEntry {

    private Integer rank;
    private Long salesUserId;
    private String salesUserName;
    private Double averageScore;
    private Integer submissionCount;
}
