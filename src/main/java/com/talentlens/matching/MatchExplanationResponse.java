package com.talentlens.matching;

import java.io.Serializable;
import java.util.List;

/**
 * Why a candidate fits a job and what they are missing. The score is the same cosine similarity used for
 * ranking, or null when the job or the candidate has no comparable embedding.
 */
public record MatchExplanationResponse(Long jobId, Long candidateId, String fullName, Double score,
                                       String summary, List<String> strengths, List<String> gaps)
        implements Serializable {
}
