package com.talentlens.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.talentlens.ai.CandidatePoolAssistant;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.common.ConflictException;
import com.talentlens.matching.EmbeddingService;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import com.talentlens.matching.MatchingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieval-augmented chat over the candidate pool: the question is embedded, the closest candidate
 * profiles are retrieved from the stored embeddings, and the chat model answers from those profiles only.
 */
@Service
@Transactional(readOnly = true)
public class CandidatePoolChatService {

    static final int DEFAULT_TOP_K = 5;

    /** Keeps the combined context of several profiles well inside the chat model's window. */
    static final int MAX_PROFILE_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore store;
    private final CandidateRepository candidateRepository;
    private final CandidatePoolAssistant assistant;

    public CandidatePoolChatService(EmbeddingModel embeddingModel, EmbeddingStore store,
                                    CandidateRepository candidateRepository, CandidatePoolAssistant assistant) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.candidateRepository = candidateRepository;
        this.assistant = assistant;
    }

    public ChatResponse chat(String question, int topK) {
        Map<Long, float[]> candidateVectors = store.findAll(EmbeddingTarget.CANDIDATE);
        if (candidateVectors.isEmpty()) {
            throw new ConflictException(
                    "No candidates have embeddings yet; POST /api/v1/candidates/{id}/embedding first");
        }

        float[] questionVector;
        try {
            questionVector = embeddingModel.embed(question);
        } catch (RuntimeException ex) {
            throw new AiUnavailableException("Embeddings are unavailable right now", ex);
        }
        if (questionVector == null || questionVector.length == 0) {
            throw new AiUnavailableException("The embedding model returned no vector", null);
        }

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<Long, float[]> entry : candidateVectors.entrySet()) {
            Double score = MatchingService.cosineSimilarity(questionVector, entry.getValue());
            if (score != null) {
                scored.add(new Scored(entry.getKey(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(Scored::candidateId));

        List<ChatSource> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (Scored match : scored) {
            if (sources.size() == topK) {
                break;
            }
            Candidate candidate = candidateRepository.findById(match.candidateId()).orElse(null);
            if (candidate == null) {
                continue;
            }
            sources.add(new ChatSource(candidate.getId(), candidate.getFullName(), candidate.getHeadline(),
                    Math.round(match.score() * 10000) / 10000.0));
            context.append("[Candidate ").append(candidate.getId()).append("] ")
                    .append(candidate.getFullName()).append('\n')
                    .append(truncate(EmbeddingService.candidateText(candidate))).append("\n\n");
        }
        if (sources.isEmpty()) {
            throw new ConflictException("No candidate embeddings match the current embedding model; "
                    + "embed the candidates again");
        }

        String answer = assistant.answer(question.trim(), context.toString().trim());
        return new ChatResponse(answer == null ? "" : answer.trim(), sources);
    }

    private static String truncate(String text) {
        return text.length() > MAX_PROFILE_LENGTH ? text.substring(0, MAX_PROFILE_LENGTH) : text;
    }

    private record Scored(Long candidateId, double score) {
    }
}
