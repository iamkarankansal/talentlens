package com.talentlens.ai;

import java.util.List;

/**
 * Structured view of a resume, as extracted by the language model.
 */
public record ResumeProfile(String headline, Double totalExperienceYears, List<String> skills,
                            List<String> education, String summary) {
}
