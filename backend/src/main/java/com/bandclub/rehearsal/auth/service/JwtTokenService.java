package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.config.AuthProperties;
import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(
            JwtEncoder encoder,
            SecretKey jwtSecretKey,
            AuthProperties properties,
            Clock clock
    ) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        this.refreshDecoder = decoder;
    }

    public TokenPair issue(User user, ClubMember membership) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());

        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .claim("login_id", user.getLoginId())
                .claim("club_id", membership.getClubId())
                .claim("role", membership.getRole().name())
                .build();

        JwtClaimsSet refreshClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(refreshExpiresAt)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("token_type", "refresh")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        String accessToken = encoder.encode(JwtEncoderParameters.from(header, accessClaims)).getTokenValue();
        String refreshToken = encoder.encode(JwtEncoderParameters.from(header, refreshClaims)).getTokenValue();

        return new TokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public Jwt decodeRefresh(String token) {
        try {
            Jwt jwt = refreshDecoder.decode(token);
            if (!"refresh".equals(jwt.getClaimAsString("token_type"))) {
                throw invalidRefreshToken();
            }
            return jwt;
        } catch (JwtException exception) {
            throw invalidRefreshToken();
        }
    }

    private AppException invalidRefreshToken() {
        return new AppException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_REFRESH_TOKEN",
                "Refresh Token이 유효하지 않습니다."
        );
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            Instant accessExpiresAt,
            Instant refreshExpiresAt
    ) {
    }
}
