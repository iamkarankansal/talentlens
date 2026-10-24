package com.talentlens.matching;

import java.io.Serializable;

/**
 * One ranked candidate. The score is the cosine similarity of the candidate's and the job's embeddings,
 * from -1 to 1, where higher means a closer match.
 */
public record CandidateMatchResponse(int rank, Long candidateId, String fullName, String headline, double score)
        implements Serializable {
}
