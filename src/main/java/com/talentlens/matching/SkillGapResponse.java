package com.talentlens.matching;

import java.util.List;

/**
 * How a candidate's parsed skills compare with the skills a job requires. {@code coverage} is the share of
 * required skills the candidate has (0 to 1), or null when the job names no skills. {@code meetsExperience}
 * is null when either the job's minimum or the candidate's experience is unknown.
 */
public record SkillGapResponse(Long jobId, Long candidateId, String fullName,
                               List<String> requiredSkills, List<String> matchedSkills,
                               List<String> missingSkills, List<String> additionalSkills, Double coverage,
                               Integer requiredExperienceYears, Double candidateExperienceYears,
                               Boolean meetsExperience) {
}
