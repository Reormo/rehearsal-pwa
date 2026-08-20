package com.bandclub.rehearsal.auth.security;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.auth.domain.UserStatus;
import com.bandclub.rehearsal.auth.repository.ClubMemberRepository;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;

    public DatabaseJwtAuthenticationConverter(
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository
    ) {
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_type"))) {
            throw new BadCredentialsException("Access token is required.");
        }

        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Invalid token subject.", exception);
        }

        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("User is not active."));

        ClubMember membership = clubMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new BadCredentialsException("Membership not found."));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + membership.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getLoginId());
    }
}
