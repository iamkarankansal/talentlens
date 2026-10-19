package com.talentlens.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.talentlens.common.UnauthorizedException;
import com.talentlens.user.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256-signed JWT access tokens whose subject is the user id.
 */
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final byte[] secret;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        if (properties.expiration() == null || properties.expiration().isNegative()
                || properties.expiration().isZero()) {
            throw new IllegalStateException("talentlens.security.jwt.expiration must be positive");
        }
        this.secret = resolveSecret(properties.secret());
        this.expiration = properties.expiration();
        this.issuer = properties.issuer();
        this.clock = clock;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("email", user.getEmail())
                .claim("role", user.getRole() == null ? null : user.getRole().name())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign access token", ex);
        }
        return new IssuedToken(jwt.serialize(), expiration.toSeconds());
    }

    /**
     * Checks the signature, algorithm, issuer and expiry of a token and returns the user id it was issued to.
     */
    public Long verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                throw invalid();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            if (!issuer.equals(claims.getIssuer()) || expiresAt == null
                    || !expiresAt.toInstant().isAfter(clock.instant())) {
                throw invalid();
            }
            return Long.valueOf(claims.getSubject());
        } catch (ParseException | JOSEException | RuntimeException ex) {
            throw invalid();
        }
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("Invalid or expired access token");
    }

    private static byte[] resolveSecret(String configured) {
        if (configured == null || configured.isBlank()) {
            log.warn("JWT_SECRET is not set; using a random signing key, so tokens will not survive a restart");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return bytes;
    }

    public record IssuedToken(String token, long expiresInSeconds) {
    }
}
