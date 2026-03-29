package com.grobird.psf.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grobird.psf.video.entity.ReferenceVideoType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    private Long id;
    private Long opportunityId;
    private Long salesSubmissionId;
    private ReferenceVideoType referenceType;
    private Long referenceVideoId;
    private Long skillsetId;
    private String skillsetName;
    private Long salesUserId;
    private String salesUserName;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
