package com.bandclub.rehearsal.admin;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.admin.service.AnnouncementService;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AdminCoreIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    AnnouncementService announcementService;

    @Autowired
    AdminActionLogService actionLogService;

    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void announcementCrudAndAuditLog() {
        long superAdminId = userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();

        var created = announcementService.create(
                superAdminId,
                "합주 공지",
                "이번 주 합주 시간을 확인해주세요.",
                true
        );
        assertTrue(created.pinned());
        assertEquals(1, announcementService.list(superAdminId).size());

        var updated = announcementService.update(
                superAdminId,
                created.id(),
                "합주 공지 수정",
                "시간표를 다시 확인해주세요.",
                false
        );
        assertEquals("합주 공지 수정", updated.title());
        assertFalse(updated.pinned());

        announcementService.delete(superAdminId, created.id());
        assertTrue(announcementService.list(superAdminId).isEmpty());

        var logs = actionLogService.list(superAdminId, 20);
        assertTrue(logs.stream().anyMatch(log -> "ANNOUNCEMENT_CREATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ANNOUNCEMENT_UPDATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ANNOUNCEMENT_DELETE".equals(log.actionType())));
    }
}
