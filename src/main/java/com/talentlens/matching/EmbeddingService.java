package com.talentlens.matching;

import java.time.Instant;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateService;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.common.ConflictException;
import com.talentlens.config.CacheConfig;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns jobs and candidate profiles into embedding vectors and stores them for semantic matching.
 */
@Service
@Transactional
public class EmbeddingService {

    /** Keeps the input well inside the embedding model's token limit. */
    static final int MAX_INPUT_LENGTH = 8000;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore store;
    private final JobService jobService;
    private final CandidateService candidateService;

    public EmbeddingService(EmbeddingModel embeddingModel, EmbeddingStore store, JobService jobService,
                            CandidateService candidateService) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.jobService = jobService;
        this.candidateService = candidateService;
    }

    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public EmbeddingResponse embedJob(Long id) {
        Job job = jobService.getOrThrow(id);
        return embed(EmbeddingTarget.JOB, id, jobText(job));
    }

    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public EmbeddingResponse embedCandidate(Long id) {
        Candidate candidate = candidateService.getOrThrow(id);
        String text = candidateText(candidate);
        if (text.isBlank()) {
            throw new ConflictException("Candidate " + id + " has no resume or profile to embed");
        }
        return embed(EmbeddingTarget.CANDIDATE, id, text);
    }

    public static String jobText(Job job) {
        StringBuilder text = new StringBuilder();
        append(text, "Title", job.getTitle());
        append(text, "Location", job.getLocation());
        if (job.getMinExperienceYears() != null) {
            append(text, "Minimum experience (years)", job.getMinExperienceYears().toString());
        }
        append(text, "Description", job.getDescription());
        return limit(text.toString());
    }

    public static String candidateText(Candidate candidate) {
        StringBuilder text = new StringBuilder();
        append(text, "Headline", candidate.getHeadline());
        if (candidate.getExperienceYears() != null) {
            append(text, "Experience (years)", candidate.getExperienceYears().toString());
        }
        if (!candidate.getSkills().isEmpty()) {
            append(text, "Skills", String.join(", ", candidate.getSkills()));
        }
        append(text, "Resume", candidate.getResumeText());
        return limit(text.toString());
    }

    private EmbeddingResponse embed(EmbeddingTarget target, Long id, String text) {
        float[] vector;
        try {
            vector = embeddingModel.embed(text);
        } catch (RuntimeException ex) {
            throw new AiUnavailableException("Embeddings are unavailable right now", ex);
        }
        if (vector == null || vector.length == 0) {
            throw new AiUnavailableException("The embedding model returned no vector", null);
        }
        Instant now = Instant.now();
        store.save(target, id, vector, now);
        return new EmbeddingResponse(target, id, vector.length, now);
    }

    private static void append(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private static String limit(String text) {
        return text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text;
    }
}
