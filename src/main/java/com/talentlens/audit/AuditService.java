package com.talentlens.audit;

import java.util.Locale;
import java.util.Optional;

import com.talentlens.auth.JwtService;
import com.talentlens.common.BadRequestException;
import com.talentlens.common.PageResponse;
import com.talentlens.common.UnauthorizedException;
import com.talentlens.user.UserAccount;
import com.talentlens.user.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes recruiter actions to the audit log inside the caller's transaction, so an action that fails leaves no
 * entry behind. The actor is taken from the bearer token of the current request, when there is a valid one.
 */
@Service
@Transactional
public class AuditService {

    public static final String JOB = "JOB";
    public static final String APPLICATION = "APPLICATION";

    static final int MAX_DETAILS_LENGTH = 500;

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuditEventRepository repository;
    private final JwtService jwtService;
    private final UserAccountRepository users;

    public AuditService(AuditEventRepository repository, JwtService jwtService, UserAccountRepository users) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.users = users;
    }

    public void record(AuditAction action, String entityType, Long entityId, String details) {
        Optional<UserAccount> actor = currentActor();
        String trimmed = details == null || details.length() <= MAX_DETAILS_LENGTH
                ? details : details.substring(0, MAX_DETAILS_LENGTH);
        repository.save(new AuditEvent(action, entityType, entityId,
                actor.map(UserAccount::getId).orElse(null), actor.map(UserAccount::getEmail).orElse(null), trimmed));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> find(String entityType, Long entityId, Pageable pageable) {
        String type = entityType == null || entityType.isBlank() ? null : entityType.trim().toUpperCase(Locale.ROOT);
        Page<AuditEvent> page;
        if (type == null) {
            if (entityId != null) {
                throw new BadRequestException("entityId can only be used together with entityType");
            }
            page = repository.findAll(pageable);
        } else if (entityId == null) {
            page = repository.findByEntityType(type, pageable);
        } else {
            page = repository.findByEntityTypeAndEntityId(type, entityId, pageable);
        }
        return PageResponse.from(page, AuditEventResponse::from);
    }

    private Optional<UserAccount> currentActor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        String header = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        try {
            return users.findById(jwtService.verify(header.substring(BEARER_PREFIX.length()).trim()));
        } catch (UnauthorizedException ex) {
            return Optional.empty();
        }
    }
}
