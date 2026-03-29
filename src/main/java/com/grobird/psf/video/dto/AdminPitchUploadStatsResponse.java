package com.grobird.psf.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPitchUploadStatsResponse {

    private String type;
    private UserUploadStats selectedUser;
    private TeamUploadStats teamStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserUploadStats {
        private Long userId;
        private String username;
        private Integer uploadCount;
        private List<DataPoint> dataPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUploadStats {
        private Double averageUploadCount;
        private Integer totalUploadCount;
        private Integer salesUserCount;
        private List<DataPoint> dataPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String period;
        private int count;
    }
}
