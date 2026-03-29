package com.grobird.psf.opportunity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanySuggestionResponse {

    private String name;
    private String domain;
    private String logo;
}
