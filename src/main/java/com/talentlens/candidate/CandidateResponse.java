package com.talentlens.candidate;

import java.time.Instant;

public record CandidateResponse(Long id, String fullName, String email, String phone,
                                boolean hasResume, Instant createdAt) {

    static CandidateResponse from(Candidate candidate) {
        String resume = candidate.getResumeText();
        return new CandidateResponse(candidate.getId(), candidate.getFullName(), candidate.getEmail(),
                candidate.getPhone(), resume != null && !resume.isBlank(), candidate.getCreatedAt());
    }
}
