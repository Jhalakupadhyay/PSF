package com.grobird.psf.qna.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QnaTargetRequest {

    /** The opportunity this Q&A is tied to. Required. */
    @NotNull(message = "opportunityId is required")
    private Long opportunityId;

    /** Optional: override target company name. If null, resolved from the opportunity's company field. */
    private String targetCompany;

    /** Offset for paginated question retrieval. Defaults to 0 (first page). */
    private Integer offset;
}
