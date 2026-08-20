package com.bandclub.rehearsal.auth;

import com.bandclub.rehearsal.auth.domain.SignupStatus;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.AuthService;
import com.bandclub.rehearsal.auth.service.InviteCodeService;
import com.bandclub.rehearsal.auth.service.SignupAdminService;
import com.bandclub.rehearsal.common.exception.AppException;
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
class AuthServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    AuthService authService;

    @Autowired
    InviteCodeService inviteCodeService;

    @Autowired
    SignupAdminService signupAdminService;

    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void signupApproveLoginRefreshAndSelfDelete() {
        long superAdminId = userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();

        String inviteCode = inviteCodeService.current(superAdminId).code();

        AuthService.SignupResult signup = authService.submitSignup(
                inviteCode,
                "member01",
                "MemberPassword123!",
                "테스트 회원"
        );
        assertEquals(SignupStatus.PENDING, signup.status());

        SignupAdminService.MemberView approved = signupAdminService.approve(superAdminId, signup.applicationId());
        assertEquals("member01", approved.loginId());

        AuthService.LoginResult login = authService.login("MEMBER01", "MemberPassword123!");
        assertEquals("member01", login.user().loginId());
        assertNotNull(login.tokens().accessToken());
        assertNotNull(login.tokens().refreshToken());

        AuthService.LoginResult refreshed = authService.refresh(login.tokens().refreshToken());
        assertEquals(login.user().id(), refreshed.user().id());
        assertNotEquals(login.tokens().refreshToken(), refreshed.tokens().refreshToken());

        authService.deleteMe(login.user().id(), "MemberPassword123!");
        assertThrows(AppException.class, () -> authService.login("member01", "MemberPassword123!"));
    }

    @Test
    @Transactional
    void rejectedLoginIdCanApplyAgain() {
        long superAdminId = userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();

        String inviteCode = inviteCodeService.current(superAdminId).code();

        AuthService.SignupResult first = authService.submitSignup(
                inviteCode,
                "retry-user",
                "MemberPassword123!",
                "재신청 회원"
        );
        signupAdminService.reject(superAdminId, first.applicationId(), "테스트 거절");

        AuthService.SignupResult second = authService.submitSignup(
                inviteCode,
                "retry-user",
                "MemberPassword123!",
                "재신청 회원"
        );

        assertEquals(SignupStatus.PENDING, second.status());
        assertNotEquals(first.applicationId(), second.applicationId());
    }
}
