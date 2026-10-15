package com.talentlens.matching;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Matching", description = "Semantic ranking of candidates for a job")
public class MatchingController {

    private final MatchingService service;

    public MatchingController(MatchingService service) {
        this.service = service;
    }

    /**
     * Ranks candidates by the cosine similarity of their embedding to the job's embedding, best first.
     */
    @GetMapping("/api/v1/jobs/{jobId}/matches")
    public CandidateRankingResponse rankCandidates(@PathVariable Long jobId,
            @RequestParam(defaultValue = "10") int limit) {
        return service.rankCandidates(jobId, limit);
    }
}
