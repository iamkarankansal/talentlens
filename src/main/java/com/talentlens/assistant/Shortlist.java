package com.talentlens.assistant;

import java.util.List;

/**
 * The agent's answer: an overall summary and the chosen candidates, best first, each with the reason.
 */
public record Shortlist(String summary, List<Pick> candidates) {

    public record Pick(Long candidateId, String reason) {
    }
}
