package com.talentlens.user;

import java.util.Locale;

import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    /**
     * @param adminEmail the account registered with this email becomes an {@code ADMIN}, so a fresh installation
     *                   has someone who can assign roles; blank means nobody is promoted automatically
     */
    public UserService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                       @Value("${talentlens.security.admin-email:}") String adminEmail) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
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
        user.setRole(!adminEmail.isEmpty() && adminEmail.equals(email) ? Role.ADMIN : Role.CANDIDATE);
        return UserResponse.from(repository.save(user));
    }

    /**
     * Gives a user another role. The last remaining admin cannot be demoted, so roles can always be managed.
     */
    public UserResponse changeRole(Long id, Role role) {
        UserAccount user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && repository.countByRole(Role.ADMIN) <= 1) {
            throw new ConflictException("The last admin cannot be given another role");
        }
        user.setRole(role);
        return UserResponse.from(repository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }
}
