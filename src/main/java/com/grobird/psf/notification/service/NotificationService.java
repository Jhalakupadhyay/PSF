package com.grobird.psf.notification.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.notification.dto.NotificationResponse;
import com.grobird.psf.notification.dto.NotificationStatusFilter;
import com.grobird.psf.notification.entity.NotificationEntity;
import com.grobird.psf.notification.repository.NotificationRepository;
import com.grobird.psf.notification.sse.SseEmitterRegistry;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               SseEmitterRegistry sseEmitterRegistry,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.userRepository = userRepository;
    }

    public void createForCompletedSubmission(SalesSubmissionEntity submission, Long salesUserId) {
        if (submission == null || salesUserId == null) {
            return;
        }

        UserEntity salesUser = userRepository.findById(salesUserId).orElse(null);
        String salesUserName = salesUser != null ? salesUser.getUsername() : null;
        Long adminUserId = salesUser != null ? salesUser.getReportedToUserId() : null;

        ReferenceVideoEntity reference = submission.getReferenceVideo();
        Instant now = Instant.now();

        NotificationEntity salesNotification = NotificationEntity.builder()
                .tenantId(submission.getTenantId())
                .targetUserId(salesUserId)
                .salesUserId(salesUserId)
                .opportunityId(submission.getOpportunityId())
                .salesSubmissionId(submission.getId())
                .referenceType(reference != null ? reference.getType() : ReferenceVideoType.GOLDEN_PITCH)
                .referenceVideoId(reference != null ? reference.getId() : null)
                .referenceVideoName(reference != null ? reference.getName() : null)
                .readAt(null)
                .createdAt(now)
                .build();
        notificationRepository.save(salesNotification);
        sseEmitterRegistry.emit(salesUserId, toResponse(salesNotification, salesUserName));

        if (adminUserId != null) {
            NotificationEntity adminNotification = NotificationEntity.builder()
                    .tenantId(submission.getTenantId())
                    .targetUserId(adminUserId)
                    .salesUserId(salesUserId)
                    .opportunityId(submission.getOpportunityId())
                    .salesSubmissionId(submission.getId())
                    .referenceType(reference != null ? reference.getType() : ReferenceVideoType.GOLDEN_PITCH)
                    .referenceVideoId(reference != null ? reference.getId() : null)
                    .referenceVideoName(reference != null ? reference.getName() : null)
                    .readAt(null)
                    .createdAt(now)
                    .build();
            notificationRepository.save(adminNotification);
            sseEmitterRegistry.emit(adminUserId, toResponse(adminNotification, salesUserName));
            log.debug("Created admin notification for admin {} about sales user {} submission", adminUserId, salesUserId);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForCurrentUser(UserPrincipal principal, NotificationStatusFilter status) {
        Long userId = getUserId(principal);
        List<NotificationEntity> entities = switch (status) {
            case UNREAD -> notificationRepository.findByTargetUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
            case READ -> notificationRepository.findByTargetUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(userId);
            case ALL -> notificationRepository.findByTargetUserIdOrderByCreatedAtDesc(userId);
        };
        
        Set<Long> salesUserIds = entities.stream()
                .map(NotificationEntity::getSalesUserId)
                .collect(Collectors.toSet());
        Map<Long, String> salesUserNames = userRepository.findAllById(salesUserIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
        
        return entities.stream()
                .map(e -> toResponse(e, salesUserNames.get(e.getSalesUserId())))
                .toList();
    }

    public NotificationResponse markAsRead(Long notificationId, UserPrincipal principal) {
        Long userId = getUserId(principal);
        NotificationEntity entity = notificationRepository.findByIdAndTargetUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (entity.getReadAt() == null) {
            entity.setReadAt(Instant.now());
            notificationRepository.save(entity);
        }
        String salesUserName = userRepository.findById(entity.getSalesUserId())
                .map(UserEntity::getUsername)
                .orElse(null);
        return toResponse(entity, salesUserName);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserPrincipal principal) {
        Long userId = getUserId(principal);
        return notificationRepository.countByTargetUserIdAndReadAtIsNull(userId);
    }

    private Long getUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getUserId();
    }

    private NotificationResponse toResponse(NotificationEntity entity, String salesUserName) {
        boolean isSkillset = entity.getReferenceType() == ReferenceVideoType.SKILLSET;
        return NotificationResponse.builder()
                .id(entity.getId())
                .opportunityId(entity.getOpportunityId())
                .salesSubmissionId(entity.getSalesSubmissionId())
                .referenceType(entity.getReferenceType())
                .referenceVideoId(entity.getReferenceVideoId())
                .skillsetId(isSkillset ? entity.getReferenceVideoId() : null)
                .skillsetName(isSkillset ? entity.getReferenceVideoName() : null)
                .salesUserId(entity.getSalesUserId())
                .salesUserName(salesUserName)
                .read(entity.getReadAt() != null)
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
