package com.talentlens.application;

import java.net.URI;
import java.util.Set;

import com.talentlens.common.PageQuery;
import com.talentlens.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Applications", description = "Candidates applying to jobs and moving through the hiring pipeline")
public class ApplicationController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "status", "createdAt", "updatedAt");

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/applications")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        ApplicationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/applications/{id}")
    public ApplicationResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/api/v1/applications/{id}/status")
    public ApplicationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ApplicationStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @DeleteMapping("/api/v1/applications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/jobs/{jobId}/applications")
    public PageResponse<ApplicationResponse> findByJob(@PathVariable Long jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.findByJob(jobId, status, PageQuery.of(page, size, sort, SORTABLE_FIELDS));
    }

    @GetMapping("/api/v1/candidates/{candidateId}/applications")
    public PageResponse<ApplicationResponse> findByCandidate(@PathVariable Long candidateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.findByCandidate(candidateId, PageQuery.of(page, size, sort, SORTABLE_FIELDS));
    }
}
