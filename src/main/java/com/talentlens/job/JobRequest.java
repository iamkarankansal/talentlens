package com.talentlens.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @NotBlank(message = "title is required") @Size(max = 255) String title,
        @NotBlank(message = "description is required") @Size(max = 10000) String description,
        @Size(max = 255) String location,
        @Min(value = 0, message = "minExperienceYears cannot be negative") Integer minExperienceYears,
        JobStatus status) {
}
