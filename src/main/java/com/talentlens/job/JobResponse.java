package com.talentlens.job;

import java.time.Instant;

public record JobResponse(Long id, String title, String description, String location,
                          Integer minExperienceYears, JobStatus status, Instant createdAt) {

    static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getTitle(), job.getDescription(), job.getLocation(),
                job.getMinExperienceYears(), job.getStatus(), job.getCreatedAt());
    }
}
