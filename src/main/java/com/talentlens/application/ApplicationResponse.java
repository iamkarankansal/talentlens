package com.talentlens.application;

import java.time.Instant;

public record ApplicationResponse(Long id, Long candidateId, String candidateName, Long jobId, String jobTitle,
                                  ApplicationStatus status, Instant createdAt, Instant updatedAt) {

    static ApplicationResponse from(Application application) {
        return new ApplicationResponse(application.getId(),
                application.getCandidate().getId(), application.getCandidate().getFullName(),
                application.getJob().getId(), application.getJob().getTitle(),
                application.getStatus(), application.getCreatedAt(), application.getUpdatedAt());
    }
}
