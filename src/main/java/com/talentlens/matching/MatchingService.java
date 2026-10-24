package com.talentlens.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.talentlens.ai.MatchExplainer;
import com.talentlens.ai.MatchExplanation;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.BadRequestException;
import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.config.CacheConfig;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranks candidates for a job by how close their stored embeddings are to the job's embedding, and explains
 * individual matches with the language model.
 */
@Service
@Transactional(readOnly = true)
public class MatchingService {

    static final int MAX_LIMIT = 50;

    private final EmbeddingStore store;
    private final JobService jobService;
    private final CandidateRepository candidateRepository;
    private final MatchExplainer matchExplainer;

    public MatchingService(EmbeddingStore store, JobService jobService, CandidateRepository candidateRepository,
                           MatchExplainer matchExplainer) {
        this.store = store;
        this.jobService = jobService;
        this.candidateRepository = candidateRepository;
        this.matchExplainer = matchExplainer;
    }

    @Cacheable(cacheNames = CacheConfig.CANDIDATE_RANKINGS, key = "#jobId + ':' + #limit")
    public CandidateRankingResponse rankCandidates(Long jobId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        jobService.getOrThrow(jobId);
        float[] jobVector = store.find(EmbeddingTarget.JOB, jobId).orElseThrow(() -> new ConflictException(
                "Job " + jobId + " has no embedding yet; POST /api/v1/jobs/" + jobId + "/embedding first"));

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<Long, float[]> entry : store.findAll(EmbeddingTarget.CANDIDATE).entrySet()) {
            Double score = cosineSimilarity(jobVector, entry.getValue());
            if (score != null) {
                scored.add(new Scored(entry.getKey(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(Scored::candidateId));

        List<CandidateMatchResponse> matches = new ArrayList<>();
        for (Scored match : scored) {
            if (matches.size() == limit) {
                break;
            }
            Candidate candidate = candidateRepository.findById(match.candidateId()).orElse(null);
            if (candidate != null) {
                matches.add(new CandidateMatchResponse(matches.size() + 1, candidate.getId(),
                        candidate.getFullName(), candidate.getHeadline(), round(match.score())));
            }
        }
        return new CandidateRankingResponse(jobId, scored.size(), matches);
    }

    @Cacheable(cacheNames = CacheConfig.MATCH_EXPLANATIONS, key = "#jobId + ':' + #candidateId")
    public MatchExplanationResponse explainMatch(Long jobId, Long candidateId) {
        Job job = jobService.getOrThrow(jobId);
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));
        String candidateText = EmbeddingService.candidateText(candidate);
        if (candidateText.isBlank()) {
            throw new ConflictException("Candidate " + candidateId + " has no resume or profile to compare");
        }

        Double score = null;
        float[] jobVector = store.find(EmbeddingTarget.JOB, jobId).orElse(null);
        float[] candidateVector = store.find(EmbeddingTarget.CANDIDATE, candidateId).orElse(null);
        if (jobVector != null && candidateVector != null) {
            Double similarity = cosineSimilarity(jobVector, candidateVector);
            score = similarity == null ? null : round(similarity);
        }

        MatchExplanation explanation = matchExplainer.explain(EmbeddingService.jobText(job), candidateText);
        if (explanation == null) {
            explanation = new MatchExplanation(null, null, null);
        }
        return new MatchExplanationResponse(jobId, candidateId, candidate.getFullName(), score,
                explanation.summary(), orEmpty(explanation.strengths()), orEmpty(explanation.gaps()));
    }

    private static List<String> orEmpty(List<String> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Cosine similarity, or null when the vectors cannot be compared (different sizes, for example after
     * switching embedding models, or an all-zero vector).
     */
    public static Double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || a.length != b.length) {
            return null;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return null;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double round(double score) {
        return Math.round(score * 10000) / 10000.0;
    }

    private record Scored(Long candidateId, double score) {
    }
}
