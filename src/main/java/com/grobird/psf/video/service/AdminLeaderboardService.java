package com.grobird.psf.video.service;

import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.dto.LeaderboardEntry;
import com.grobird.psf.video.dto.SalesLeaderboardResponse;
import com.grobird.psf.video.entity.SalesDashboardMetricsEntity;
import com.grobird.psf.video.repository.SalesDashboardMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminLeaderboardService {

    private static final int LEADERBOARD_SIZE = 5;

    private final SalesDashboardMetricsRepository metricsRepository;
    private final UserRepository userRepository;

    public AdminLeaderboardService(SalesDashboardMetricsRepository metricsRepository,
                                   UserRepository userRepository) {
        this.metricsRepository = metricsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SalesLeaderboardResponse getLeaderboard(Long tenantId, Long adminUserId) {
        List<UserEntity> salesUsers = userRepository.findByReportedToUserId(adminUserId);
        if (salesUsers.isEmpty()) {
            return SalesLeaderboardResponse.builder()
                    .leaderboard(List.of())
                    .totalSalesUsers(0)
                    .build();
        }

        Map<Long, UserEntity> userMap = salesUsers.stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));

        List<SalesDashboardMetricsEntity> metrics = metricsRepository
                .findByAdminUserId(adminUserId);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (SalesDashboardMetricsEntity m : metrics) {
            if (rank > LEADERBOARD_SIZE) {
                break;
            }
            UserEntity user = userMap.get(m.getUserId());
            if (user == null) {
                continue;
            }
            entries.add(LeaderboardEntry.builder()
                    .rank(rank++)
                    .salesUserId(m.getUserId())
                    .salesUserName(user.getUsername())
                    .averageScore(m.getAverageScore() != null ? m.getAverageScore().doubleValue() : null)
                    .submissionCount(m.getSubmissionCount())
                    .build());
        }

        return SalesLeaderboardResponse.builder()
                .leaderboard(entries)
                .totalSalesUsers(salesUsers.size())
                .build();
    }
}
