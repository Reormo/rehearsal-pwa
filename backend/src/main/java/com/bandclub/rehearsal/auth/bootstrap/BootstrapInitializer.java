package com.bandclub.rehearsal.auth.bootstrap;

import com.bandclub.rehearsal.auth.config.BootstrapProperties;
import com.bandclub.rehearsal.auth.domain.*;
import com.bandclub.rehearsal.auth.repository.*;
import com.bandclub.rehearsal.auth.service.InviteCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Component
public class BootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);

    private final BootstrapProperties properties;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    public BootstrapInitializer(
            BootstrapProperties properties,
            ClubRepository clubRepository,
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository,
            InviteCodeRepository inviteCodeRepository,
            PasswordEncoder passwordEncoder,
            InviteCodeGenerator inviteCodeGenerator,
            Clock clock
    ) {
        this.properties = properties;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        Club club = clubRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> clubRepository.save(Club.create(normalizedClubName(), now)));

        var existingSuperAdmin = clubMemberRepository.findByClubIdAndRole(club.getId(), ClubRole.SUPER_ADMIN);
        if (existingSuperAdmin.isPresent()) {
            ensureInviteCode(club.getId(), existingSuperAdmin.get().getUserId(), now);
            return;
        }

        if (allAdminFieldsBlank()) {
            log.warn("No SUPER_ADMIN exists and INITIAL_ADMIN_* values are not configured.");
            return;
        }
        if (anyAdminFieldBlank()) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_LOGIN_ID, INITIAL_ADMIN_PASSWORD, and INITIAL_ADMIN_NAME must all be configured."
            );
        }

        String loginId = properties.initialAdminLoginId().trim().toLowerCase(Locale.ROOT);
        String adminName = properties.initialAdminName().trim();
        String adminPassword = properties.initialAdminPassword();

        if (loginId.length() < 4 || loginId.length() > 50) {
            throw new IllegalStateException("INITIAL_ADMIN_LOGIN_ID must be 4 to 50 characters.");
        }
        if (adminName.isBlank() || adminName.length() > 50) {
            throw new IllegalStateException("INITIAL_ADMIN_NAME must be 1 to 50 characters.");
        }
        if (adminPassword.length() < 8 || adminPassword.length() > 72) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must be 8 to 72 characters.");
        }

        if (userRepository.existsByLoginIdIgnoreCaseAndDeletedAtIsNull(loginId)) {
            throw new IllegalStateException("INITIAL_ADMIN_LOGIN_ID is already used by another active user.");
        }

        User user = userRepository.save(User.active(
                loginId,
                passwordEncoder.encode(adminPassword),
                adminName,
                now
        ));
        clubMemberRepository.save(ClubMember.join(club.getId(), user.getId(), ClubRole.SUPER_ADMIN, now));
        ensureInviteCode(club.getId(), user.getId(), now);

        log.info("Initial SUPER_ADMIN created for login id '{}'.", loginId);
    }

    private void ensureInviteCode(Long clubId, Long superAdminUserId, Instant now) {
        if (inviteCodeRepository.findByClubIdAndRevokedAtIsNull(clubId).isPresent()) {
            return;
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String code = inviteCodeGenerator.next();
            if (!inviteCodeRepository.existsByCode(code)) {
                inviteCodeRepository.save(InviteCode.issue(clubId, code, superAdminUserId, now));
                return;
            }
        }
        throw new IllegalStateException("Failed to generate the initial invite code.");
    }

    private String normalizedClubName() {
        String value = properties.clubName();
        return value == null || value.isBlank() ? "밴드 동아리" : value.trim();
    }

    private boolean allAdminFieldsBlank() {
        return isBlank(properties.initialAdminLoginId())
                && isBlank(properties.initialAdminPassword())
                && isBlank(properties.initialAdminName());
    }

    private boolean anyAdminFieldBlank() {
        return isBlank(properties.initialAdminLoginId())
                || isBlank(properties.initialAdminPassword())
                || isBlank(properties.initialAdminName());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
