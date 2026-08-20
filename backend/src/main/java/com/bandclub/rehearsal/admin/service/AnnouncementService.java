package com.bandclub.rehearsal.admin.service;

import com.bandclub.rehearsal.admin.domain.Announcement;
import com.bandclub.rehearsal.admin.repository.AnnouncementRepository;
import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AnnouncementService {

    private final MembershipService membershipService;
    private final AnnouncementRepository repository;
    private final UserRepository userRepository;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public AnnouncementService(
            MembershipService membershipService,
            AnnouncementRepository repository,
            UserRepository userRepository,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.repository = repository;
        this.userRepository = userRepository;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementView> list(Long userId) {
        var membership = membershipService.requireMembership(userId);
        return repository.findAllByClubIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(membership.getClubId())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementView> listAdmin(Long userId) {
        var membership = membershipService.requireAdmin(userId);
        return repository.findAllByClubIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(membership.getClubId())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AnnouncementView create(Long actorUserId, String titleValue, String contentValue, boolean pinned) {
        var membership = membershipService.requireAdmin(actorUserId);
        Instant now = clock.instant();

        Announcement announcement = repository.save(Announcement.create(
                membership.getClubId(),
                normalizeTitle(titleValue),
                normalizeContent(contentValue),
                pinned,
                actorUserId,
                now
        ));

        AnnouncementView view = toView(announcement);
        actionLogService.record(
                actorUserId,
                "ANNOUNCEMENT_CREATE",
                "ANNOUNCEMENT",
                announcement.getId(),
                null,
                null,
                snapshot(view)
        );
        return view;
    }

    @Transactional
    public AnnouncementView update(
            Long actorUserId,
            Long announcementId,
            String titleValue,
            String contentValue,
            boolean pinned
    ) {
        var membership = membershipService.requireAdmin(actorUserId);
        Announcement announcement = requireAnnouncement(announcementId, membership.getClubId());
        AnnouncementView before = toView(announcement);

        announcement.update(
                normalizeTitle(titleValue),
                normalizeContent(contentValue),
                pinned,
                clock.instant()
        );

        AnnouncementView after = toView(announcement);
        actionLogService.record(
                actorUserId,
                "ANNOUNCEMENT_UPDATE",
                "ANNOUNCEMENT",
                announcementId,
                null,
                snapshot(before),
                snapshot(after)
        );
        return after;
    }

    @Transactional
    public void delete(Long actorUserId, Long announcementId) {
        var membership = membershipService.requireAdmin(actorUserId);
        Announcement announcement = requireAnnouncement(announcementId, membership.getClubId());
        AnnouncementView before = toView(announcement);

        announcement.delete(clock.instant());

        actionLogService.record(
                actorUserId,
                "ANNOUNCEMENT_DELETE",
                "ANNOUNCEMENT",
                announcementId,
                null,
                snapshot(before),
                Map.of("deleted", true)
        );
    }

    private Announcement requireAnnouncement(Long id, Long clubId) {
        return repository.findByIdAndClubIdAndDeletedAtIsNull(id, clubId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "ANNOUNCEMENT_NOT_FOUND",
                        "공지를 찾을 수 없습니다."
                ));
    }

    private AnnouncementView toView(Announcement announcement) {
        String authorName = announcement.getAuthorUserId() == null
                ? "시스템"
                : userRepository.findById(announcement.getAuthorUserId())
                .map(User::getName)
                .orElse("삭제된 사용자");

        return new AnnouncementView(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.isPinned(),
                announcement.getAuthorUserId(),
                authorName,
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }

    private Map<String, Object> snapshot(AnnouncementView view) {
        return Map.of(
                "title", view.title(),
                "content", view.content(),
                "pinned", view.pinned()
        );
    }

    private String normalizeTitle(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ANNOUNCEMENT_TITLE_REQUIRED", "공지 제목을 입력해주세요.");
        }
        if (normalized.length() > 200) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ANNOUNCEMENT_TITLE_TOO_LONG", "공지 제목은 200자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String normalizeContent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ANNOUNCEMENT_CONTENT_REQUIRED", "공지 내용을 입력해주세요.");
        }
        return normalized;
    }

    public record AnnouncementView(
            Long id,
            String title,
            String content,
            boolean pinned,
            Long authorUserId,
            String authorName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
