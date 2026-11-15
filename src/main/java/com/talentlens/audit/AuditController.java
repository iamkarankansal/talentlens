package com.talentlens.audit;

import java.util.Set;

import com.talentlens.auth.AuthService;
import com.talentlens.common.PageQuery;
import com.talentlens.common.PageResponse;
import com.talentlens.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Audit log", description = "Recruiter actions on jobs and applications, readable by admins")
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "action", "occurredAt");

    private final AuditService service;
    private final AuthService authService;

    public AuditController(AuditService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> findAll(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.requireRole(authorization, Role.ADMIN);
        return service.find(entityType, entityId, PageQuery.of(page, size, sort, SORTABLE_FIELDS));
    }
}
