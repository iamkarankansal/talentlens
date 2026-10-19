package com.talentlens.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.talentlens.common.ForbiddenException;
import com.talentlens.common.UnauthorizedException;
import com.talentlens.user.Role;
import com.talentlens.user.UserAccount;
import com.talentlens.user.UserAccountRepository;
import com.talentlens.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Checked against unknown emails so they take as long as a wrong password.
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> user = repository.findByEmailIgnoreCase(email);
        String hash = user.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new UnauthorizedException("Invalid email or password");
        }
        JwtService.IssuedToken token = jwtService.issue(user.get());
        return new TokenResponse(token.token(), "Bearer", token.expiresInSeconds());
    }

    public UserResponse currentUser(String authorizationHeader) {
        return UserResponse.from(authenticate(authorizationHeader));
    }

    /**
     * Resolves the user a bearer token belongs to, or fails with {@code 401}. The user's role is read from the
     * database rather than the token, so a role change applies to tokens issued before it.
     */
    public UserAccount authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            throw new UnauthorizedException("A bearer access token is required");
        }
        Long userId = jwtService.verify(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
        return repository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired access token"));
    }

    /**
     * Like {@link #authenticate(String)}, but also fails with {@code 403} unless the user has one of the roles.
     */
    public UserAccount requireRole(String authorizationHeader, Role... allowed) {
        UserAccount user = authenticate(authorizationHeader);
        if (!Arrays.asList(allowed).contains(user.getRole())) {
            throw new ForbiddenException("This action requires one of the roles " + Arrays.toString(allowed));
        }
        return user;
    }
}
