package com.talentlens.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.matching.CandidateMatchResponse;
import com.talentlens.matching.EmbeddingService;
import com.talentlens.matching.MatchingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools the shortlisting agent may call while it works on one job. Created per request, bound to that
 * job, and records what the model looked at so the service can check its answer against real data.
 */
public class ShortlistTools {

    static final int MAX_RANKED = 30;

    private final Long jobId;
    private final MatchingService matchingService;
    private final CandidateRepository candidateRepository;
    private final Map<Long, Double> scores = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();

    public ShortlistTools(Long jobId, MatchingService matchingService, CandidateRepository candidateRepository) {
        this.jobId = jobId;
        this.matchingService = matchingService;
        this.candidateRepository = candidateRepository;
    }

    @Tool(description = "Rank the candidates for the job by semantic similarity of their profile to the job, "
            + "best first. Returns candidate id, name, headline and a score from -1 to 1.")
    public List<CandidateMatchResponse> rankCandidates(
            @ToolParam(description = "How many top candidates to return, from 1 to 30") int limit) {
        calls.add("rankCandidates");
        int bounded = Math.max(1, Math.min(limit, MAX_RANKED));
        List<CandidateMatchResponse> matches = matchingService.rankCandidates(jobId, bounded).matches();
        matches.forEach(match -> scores.put(match.candidateId(), match.score()));
        return matches;
    }

    @Tool(description = "Read one candidate's full profile and resume text, to check them against the job.")
    public String candidateProfile(@ToolParam(description = "The candidate id") Long candidateId) {
        calls.add("candidateProfile");
        Candidate candidate = candidateId == null ? null : candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            return "No candidate with id " + candidateId;
        }
        String text = EmbeddingService.candidateText(candidate);
        return "Candidate " + candidate.getId() + ": " + candidate.getFullName() + "\n"
                + (text.isBlank() ? "No resume or profile on file." : text);
    }

    /**
     * The similarity score the ranking tool reported for the candidate, or null if it never ranked them.
     */
    Double scoreOf(Long candidateId) {
        return scores.get(candidateId);
    }

    List<String> calls() {
        return List.copyOf(calls);
    }
}
