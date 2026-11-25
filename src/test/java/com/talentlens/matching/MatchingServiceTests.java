package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.talentlens.ai.MatchExplainer;
import com.talentlens.ai.MatchExplanation;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.BadRequestException;
import com.talentlens.common.ConflictException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingServiceTests {

    private static final Long JOB_ID = 1L;

    private EmbeddingStore store;
    private JobService jobService;
    private CandidateRepository candidateRepository;
    private MatchExplainer matchExplainer;
    private MatchingService service;
    private Job job;
    private final Map<Long, Candidate> candidates = new HashMap<>();

    @BeforeEach
    void setUp() {
        store = mock(EmbeddingStore.class);
        jobService = mock(JobService.class);
        candidateRepository = mock(CandidateRepository.class);
        matchExplainer = mock(MatchExplainer.class);
        service = new MatchingService(store, jobService, candidateRepository, matchExplainer);

        job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring services");
        when(jobService.getOrThrow(JOB_ID)).thenReturn(job);

        when(candidateRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Candidate> found = new ArrayList<>();
            for (Long id : ids) {
                if (candidates.containsKey(id)) {
                    found.add(candidates.get(id));
                }
            }
            return found;
        });
        when(candidateRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(candidates.get(invocation.<Long>getArgument(0))));
    }

    @Test
    void ranksComparableCandidatesBestFirstAndSkipsTheRest() {
        when(store.find(EmbeddingTarget.JOB, JOB_ID)).thenReturn(Optional.of(new float[] {1, 0}));
        Map<Long, float[]> vectors = new LinkedHashMap<>();
        vectors.put(10L, new float[] {0, 1});
        vectors.put(11L, new float[] {1, 1});
        vectors.put(12L, new float[] {2, 0});
        vectors.put(13L, new float[] {1, 0, 0});
        vectors.put(14L, new float[] {0, 0});
        when(store.findAll(EmbeddingTarget.CANDIDATE)).thenReturn(vectors);
        for (long id = 10; id <= 14; id++) {
            candidate(id, "Candidate " + id);
        }

        CandidateRankingResponse ranking = service.rankCandidates(JOB_ID, 2);

        assertThat(ranking.jobId()).isEqualTo(JOB_ID);
        assertThat(ranking.candidatesConsidered()).isEqualTo(3);
        assertThat(ranking.matches()).extracting(CandidateMatchResponse::candidateId).containsExactly(12L, 11L);
        assertThat(ranking.matches()).extracting(CandidateMatchResponse::rank).containsExactly(1, 2);
        assertThat(ranking.matches().get(0).score()).isEqualTo(1.0);
        assertThat(ranking.matches().get(1).score()).isEqualTo(0.7071);
        assertThat(ranking.matches().get(0).fullName()).isEqualTo("Candidate 12");
    }

    @Test
    void fillsThePlaceOfACandidateDeletedSinceItsVectorWasRead() {
        when(store.find(EmbeddingTarget.JOB, JOB_ID)).thenReturn(Optional.of(new float[] {1, 0}));
        Map<Long, float[]> vectors = new LinkedHashMap<>();
        vectors.put(20L, new float[] {1, 0});
        vectors.put(21L, new float[] {1, 1});
        vectors.put(22L, new float[] {0, 1});
        when(store.findAll(EmbeddingTarget.CANDIDATE)).thenReturn(vectors);
        candidate(21L, "Second");
        candidate(22L, "Third");

        CandidateRankingResponse ranking = service.rankCandidates(JOB_ID, 2);

        assertThat(ranking.candidatesConsidered()).isEqualTo(3);
        assertThat(ranking.matches()).extracting(CandidateMatchResponse::candidateId).containsExactly(21L, 22L);
        assertThat(ranking.matches()).extracting(CandidateMatchResponse::rank).containsExactly(1, 2);
    }

    @Test
    void rejectsALimitOutsideTheAllowedRange() {
        assertThatThrownBy(() -> service.rankCandidates(JOB_ID, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.rankCandidates(JOB_ID, MatchingService.MAX_LIMIT + 1))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(store);
    }

    @Test
    void refusesToRankForAJobWithoutAnEmbedding() {
        when(store.find(EmbeddingTarget.JOB, JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rankCandidates(JOB_ID, 5))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no embedding");
    }

    @Test
    void explainsAMatchWithTheSimilarityScoreAndCleanedLists() {
        Candidate candidate = candidate(30L, "Asha Rao");
        candidate.setResumeText("Eight years of Java and Spring Boot.");
        when(store.find(EmbeddingTarget.JOB, JOB_ID)).thenReturn(Optional.of(new float[] {1, 0}));
        when(store.find(EmbeddingTarget.CANDIDATE, 30L)).thenReturn(Optional.of(new float[] {1, 1}));
        when(matchExplainer.explain(anyString(), anyString())).thenReturn(new MatchExplanation(
                "Strong backend fit", Arrays.asList("Java", null), null));

        MatchExplanationResponse response = service.explainMatch(JOB_ID, 30L);

        assertThat(response.fullName()).isEqualTo("Asha Rao");
        assertThat(response.score()).isCloseTo(0.7071, within(0.0001));
        assertThat(response.summary()).isEqualTo("Strong backend fit");
        assertThat(response.strengths()).containsExactly("Java");
        assertThat(response.gaps()).isEmpty();
    }

    @Test
    void explainsWithoutAScoreWhenAVectorIsMissingAndToleratesAnEmptyAnswer() {
        Candidate candidate = candidate(31L, "Ben Ode");
        candidate.setResumeText("Python developer.");
        when(store.find(EmbeddingTarget.JOB, JOB_ID)).thenReturn(Optional.of(new float[] {1, 0}));
        when(store.find(EmbeddingTarget.CANDIDATE, 31L)).thenReturn(Optional.empty());
        when(matchExplainer.explain(anyString(), anyString())).thenReturn(null);

        MatchExplanationResponse response = service.explainMatch(JOB_ID, 31L);

        assertThat(response.score()).isNull();
        assertThat(response.summary()).isNull();
        assertThat(response.strengths()).isEmpty();
        assertThat(response.gaps()).isEmpty();
    }

    @Test
    void refusesToExplainACandidateWithNothingToCompare() {
        candidate(32L, "Empty Profile");

        assertThatThrownBy(() -> service.explainMatch(JOB_ID, 32L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no resume or profile");
        verifyNoInteractions(matchExplainer);
    }

    @Test
    void cosineSimilarityHandlesDirectionAndIncomparableVectors() {
        assertThat(MatchingService.cosineSimilarity(new float[] {1, 2}, new float[] {2, 4}))
                .isCloseTo(1.0, within(1e-9));
        assertThat(MatchingService.cosineSimilarity(new float[] {1, 0}, new float[] {-1, 0}))
                .isCloseTo(-1.0, within(1e-9));
        assertThat(MatchingService.cosineSimilarity(new float[] {1, 0}, new float[] {0, 1}))
                .isCloseTo(0.0, within(1e-9));
        assertThat(MatchingService.cosineSimilarity(new float[] {1, 0}, new float[] {1, 0, 0})).isNull();
        assertThat(MatchingService.cosineSimilarity(new float[0], new float[0])).isNull();
        assertThat(MatchingService.cosineSimilarity(new float[] {0, 0}, new float[] {1, 0})).isNull();
    }

    private Candidate candidate(Long id, String fullName) {
        Candidate candidate = new Candidate();
        candidate.setFullName(fullName);
        candidate.setEmail("candidate" + id + "@example.com");
        ReflectionTestUtils.setField(candidate, "id", id);
        candidates.put(id, candidate);
        return candidate;
    }
}
