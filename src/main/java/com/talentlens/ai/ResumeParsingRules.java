package com.talentlens.ai;

import java.util.List;

import com.talentlens.common.ConflictException;

/**
 * Guards around the resume parsing call: resumes too short to say anything are refused before the model is
 * asked, overlong resumes are cut so the prompt stays within the model's limits, and experience values no
 * resume could support are dropped from the model's answer.
 */
final class ResumeParsingRules {

    /** Fewer characters than this, after trimming, cannot hold a parseable resume. */
    static final int MIN_LENGTH = 20;

    /** Characters sent to the model at most; the rest of a longer resume is cut off. */
    static final int MAX_LENGTH = 20_000;

    /** Total experience above this many years is treated as a parsing mistake. */
    static final double MAX_EXPERIENCE_YEARS = 60.0;

    private ResumeParsingRules() {
    }

    /**
     * The resume text to send to the model: trimmed and cut to {@link #MAX_LENGTH}.
     *
     * @throws ConflictException when the candidate has no resume or one too short to parse
     */
    static String prepare(Long candidateId, String resume) {
        String text = resume == null ? "" : resume.strip();
        if (text.isEmpty()) {
            throw new ConflictException("Candidate " + candidateId + " has no resume text to parse");
        }
        if (text.length() < MIN_LENGTH) {
            throw new ConflictException("Candidate " + candidateId + " has a resume too short to parse; at least "
                    + MIN_LENGTH + " characters are needed");
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }

    /**
     * The model's answer with implausible experience dropped; a missing answer becomes an empty profile.
     */
    static ResumeProfile plausible(ResumeProfile profile) {
        if (profile == null) {
            return new ResumeProfile(null, null, List.of(), List.of(), null);
        }
        Double years = profile.totalExperienceYears();
        if (years != null && (years.isNaN() || years < 0 || years > MAX_EXPERIENCE_YEARS)) {
            years = null;
        }
        return new ResumeProfile(profile.headline(), years, profile.skills(), profile.education(),
                profile.summary());
    }
}
