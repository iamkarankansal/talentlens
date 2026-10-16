package com.talentlens.ai;

import java.util.List;

/**
 * Why a candidate fits a job, as judged by the language model: a short summary, the requirements the
 * candidate meets, and the requirements they are missing.
 */
public record MatchExplanation(String summary, List<String> strengths, List<String> gaps) {
}
