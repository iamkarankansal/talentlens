package com.talentlens.matching;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.talentlens.ai.ModelCallMetrics;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.candidate.CandidateService;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.common.ConflictException;
import com.talentlens.config.CacheConfig;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
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

    /** Texts sent to the embedding model in one request when embedding candidates in bulk. */
    static final int BATCH_SIZE = 100;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore store;
    private final JobService jobService;
    private final CandidateService candidateService;
    private final CandidateRepository candidateRepository;
    private final ModelCallMetrics metrics;

    public EmbeddingService(EmbeddingModel embeddingModel, EmbeddingStore store, JobService jobService,
                            CandidateService candidateService, CandidateRepository candidateRepository,
                            ModelCallMetrics metrics) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.jobService = jobService;
        this.candidateService = candidateService;
        this.candidateRepository = candidateRepository;
        this.metrics = metrics;
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

    /**
     * Embeds every candidate that has no stored embedding yet, sending their texts to the model in batches of
     * {@link #BATCH_SIZE}. Candidates with nothing to embed are skipped. Vectors are only stored once every
     * batch has succeeded, so a model failure leaves the stored embeddings unchanged.
     */
    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public BatchEmbeddingResponse embedMissingCandidates() {
        Set<Long> embedded = store.ids(EmbeddingTarget.CANDIDATE);
        List<Long> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int skipped = 0;
        for (Candidate candidate : candidateRepository.findAll(Sort.by("id"))) {
            if (embedded.contains(candidate.getId())) {
                continue;
            }
            String text = candidateText(candidate);
            if (text.isBlank()) {
                skipped++;
                continue;
            }
            ids.add(candidate.getId());
            texts.add(text);
        }
        List<float[]> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        Instant now = Instant.now();
        for (int i = 0; i < ids.size(); i++) {
            store.save(EmbeddingTarget.CANDIDATE, ids.get(i), vectors.get(i), now);
        }
        return new BatchEmbeddingResponse(ids.size(), skipped, List.copyOf(ids), now);
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
            vector = metrics.record(ModelCallMetrics.EMBEDDING, () -> embeddingModel.embed(text));
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

    private List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors;
        try {
            vectors = metrics.record(ModelCallMetrics.EMBEDDING, () -> embeddingModel.embed(texts));
        } catch (RuntimeException ex) {
            throw new AiUnavailableException("Embeddings are unavailable right now", ex);
        }
        if (vectors == null || vectors.size() != texts.size()) {
            throw new AiUnavailableException("The embedding model returned the wrong number of vectors", null);
        }
        for (float[] vector : vectors) {
            if (vector == null || vector.length == 0) {
                throw new AiUnavailableException("The embedding model returned no vector", null);
            }
        }
        return vectors;
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
