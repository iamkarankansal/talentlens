package com.talentlens.matching;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Matching",
        description = "Semantic ranking of candidates for a job, match explanations and skill-gap reports")
public class MatchingController {

    private final MatchingService service;
    private final SkillGapService skillGapService;

    public MatchingController(MatchingService service, SkillGapService skillGapService) {
        this.service = service;
        this.skillGapService = skillGapService;
    }

    /**
     * Ranks candidates by the cosine similarity of their embedding to the job's embedding, best first.
     */
    @GetMapping("/api/v1/jobs/{jobId}/matches")
    public CandidateRankingResponse rankCandidates(@PathVariable Long jobId,
            @RequestParam(defaultValue = "10") int limit) {
        return service.rankCandidates(jobId, limit);
    }

    /**
     * Explains why the candidate fits the job and what they are missing, using the language model.
     */
    @GetMapping("/api/v1/jobs/{jobId}/matches/{candidateId}/explanation")
    public MatchExplanationResponse explainMatch(@PathVariable Long jobId, @PathVariable Long candidateId) {
        return service.explainMatch(jobId, candidateId);
    }

    /**
     * Compares the skills the job requires with the candidate's parsed skills: matched, missing and extra.
     */
    @GetMapping("/api/v1/jobs/{jobId}/matches/{candidateId}/skill-gap")
    public SkillGapResponse skillGap(@PathVariable Long jobId, @PathVariable Long candidateId) {
        return skillGapService.report(jobId, candidateId);
    }
}
