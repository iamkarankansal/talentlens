package com.talentlens.assistant;

import java.util.List;

/**
 * A shortlist chosen by the agent. {@code score} is the similarity the ranking tool reported, or null when
 * the agent picked a candidate it did not rank. {@code toolCalls} lists the tools the agent called, in order.
 */
public record ShortlistResponse(Long jobId, String jobTitle, String summary, List<Entry> candidates,
                                List<String> toolCalls) {

    public record Entry(int rank, Long candidateId, String fullName, String headline, Double score,
                        String reason) {
    }
}
