package com.grobird.psf.user.dto;

import com.grobird.psf.user.enums.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SalesUserDetailResponse {

    private Long id;

    private String name;

    private String department;

    private String email;

    private String phoneNumber;

    private Instant createdAt;

    private InvitationStatus invitationStatus;

    /** Average score from completed submissions; null if no submissions yet. */
    private BigDecimal averageScore;

    /** Date of the latest opportunity created by this sales user; null if no opportunities. */
    private Instant latestOpportunityDate;
}
