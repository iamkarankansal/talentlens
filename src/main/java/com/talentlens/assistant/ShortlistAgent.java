package com.talentlens.assistant;

/**
 * Picks a shortlist for a job by letting the language model call {@link ShortlistTools}.
 */
public interface ShortlistAgent {

    Shortlist shortlist(String jobText, int size, ShortlistTools tools);
}
