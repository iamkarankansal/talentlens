package com.talentlens.matching;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Embeddings", description = "Vector embeddings of jobs and candidates for semantic matching")
public class EmbeddingController {

    private final EmbeddingService service;

    public EmbeddingController(EmbeddingService service) {
        this.service = service;
    }

    /**
     * Embeds the job's title, location, experience and description, replacing any earlier embedding.
     */
    @PostMapping("/api/v1/jobs/{jobId}/embedding")
    public EmbeddingResponse embedJob(@PathVariable Long jobId) {
        return service.embedJob(jobId);
    }

    /**
     * Embeds the candidate's parsed profile and resume text, replacing any earlier embedding.
     */
    @PostMapping("/api/v1/candidates/{candidateId}/embedding")
    public EmbeddingResponse embedCandidate(@PathVariable Long candidateId) {
        return service.embedCandidate(candidateId);
    }
}
