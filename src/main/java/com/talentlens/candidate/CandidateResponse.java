package com.talentlens.candidate;

import java.time.Instant;
import java.util.List;

public record CandidateResponse(Long id, String fullName, String email, String phone,
                                boolean hasResume, String headline, Double experienceYears,
                                List<String> skills, Instant profileParsedAt, Instant createdAt) {

    static CandidateResponse from(Candidate candidate) {
        String resume = candidate.getResumeText();
        return new CandidateResponse(candidate.getId(), candidate.getFullName(), candidate.getEmail(),
                candidate.getPhone(), resume != null && !resume.isBlank(), candidate.getHeadline(),
                candidate.getExperienceYears(), List.copyOf(candidate.getSkills()),
                candidate.getProfileParsedAt(), candidate.getCreatedAt());
    }
}
