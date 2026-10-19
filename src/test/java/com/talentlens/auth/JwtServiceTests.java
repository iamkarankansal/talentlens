package com.talentlens.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nimbusds.jwt.SignedJWT;
import com.talentlens.common.UnauthorizedException;
import com.talentlens.user.Role;
import com.talentlens.user.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTests {

    private static final String SECRET = "a-test-signing-secret-that-is-long-enough";
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @Test
    void verifiesItsOwnTokenAndReturnsTheUserId() {
        JwtService service = service(SECRET, "talentlens", NOW);

        JwtService.IssuedToken token = service.issue(user(42L));

        assertThat(token.expiresInSeconds()).isEqualTo(3600);
        assertThat(service.verify(token.token())).isEqualTo(42L);
    }

    @Test
    void carriesTheUsersRoleAsAClaim() throws Exception {
        UserAccount recruiter = user(42L);
        recruiter.setRole(Role.RECRUITER);

        String token = service(SECRET, "talentlens", NOW).issue(recruiter).token();

        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("role")).isEqualTo("RECRUITER");
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = service(SECRET, "talentlens", NOW).issue(user(42L)).token();
        JwtService later = service(SECRET, "talentlens", NOW.plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> later.verify(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsATokenSignedWithAnotherKeyOrIssuer() {
        String token = service(SECRET, "talentlens", NOW).issue(user(42L)).token();

        assertThatThrownBy(() -> service("another-signing-secret-that-is-long-enough", "talentlens", NOW)
                .verify(token)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service(SECRET, "someone-else", NOW).verify(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refusesASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> service("too-short", "talentlens", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatesARandomKeyWhenNoSecretIsConfigured() {
        JwtService first = service("", "talentlens", NOW);
        JwtService second = service(null, "talentlens", NOW);
        String token = first.issue(user(7L)).token();

        assertThat(first.verify(token)).isEqualTo(7L);
        assertThatThrownBy(() -> second.verify(token)).isInstanceOf(UnauthorizedException.class);
    }

    private static JwtService service(String secret, String issuer, Instant now) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(1), issuer),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static UserAccount user(Long id) {
        UserAccount user = new UserAccount();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("User " + id);
        return user;
    }
}
