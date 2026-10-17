package com.talentlens.user;

import java.util.Locale;

import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return UserResponse.from(repository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }
}
