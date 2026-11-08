package com.talentlens.application;

import java.util.Map;

/**
 * How many applications a job has in each hiring stage. {@code stages} lists every stage in pipeline order,
 * including those with no applications.
 */
public record ApplicationPipelineResponse(Long jobId, String jobTitle, long total,
                                          Map<ApplicationStatus, Long> stages) {
}
