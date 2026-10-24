package com.talentlens.matching;

import java.io.Serializable;
import java.util.List;

/**
 * Candidates ranked for a job, best match first, out of all candidates that have an embedding
 * comparable with the job's.
 */
public record CandidateRankingResponse(Long jobId, int candidatesConsidered, List<CandidateMatchResponse> matches)
        implements Serializable {
}
