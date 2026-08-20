package com.bandclub.rehearsal.admin.service;

import com.bandclub.rehearsal.admin.domain.AdminActionLog;
import com.bandclub.rehearsal.admin.repository.AdminActionLogRepository;
import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.MembershipService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdminActionLogService {

    private final MembershipService membershipService;
    private final AdminActionLogRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdminActionLogService(
            MembershipService membershipService,
            AdminActionLogRepository repository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(
            Long actorUserId,
            String actionType,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> beforeData,
            Map<String, Object> afterData
    ) {
        var membership = membershipService.requireAdmin(actorUserId);
        repository.save(AdminActionLog.create(
                membership.getClubId(),
                actorUserId,
                actionType,
                targetType,
                targetId,
                blankToNull(reason),
                beforeData,
                afterData,
                clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public List<LogView> list(Long actorUserId, int limit) {
        var membership = membershipService.requireAdmin(actorUserId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findAllByClubIdOrderByCreatedAtDesc(
                        membership.getClubId(),
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(this::toView)
                .toList();
    }

    private LogView toView(AdminActionLog log) {
        String actorName = log.getActorUserId() == null
                ? "시스템"
                : userRepository.findById(log.getActorUserId())
                .map(User::getName)
                .orElse("삭제된 사용자");

        return new LogView(
                log.getId(),
                log.getActorUserId(),
                actorName,
                log.getActionType(),
                log.getTargetType(),
                log.getTargetId(),
                log.getReason(),
                log.getBeforeData(),
                log.getAfterData(),
                log.getCreatedAt()
        );
    }


    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record LogView(
            Long id,
            Long actorUserId,
            String actorName,
            String actionType,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Instant createdAt
    ) {
    }
}
