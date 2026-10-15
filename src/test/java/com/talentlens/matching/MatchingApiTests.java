package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MatchingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void ranksCandidatesByCosineSimilarityToTheJob() throws Exception {
        Long jobId = job(new float[] {1f, 0f, 0f});
        Long far = candidate("Far Away", "far@example.com", new float[] {0f, 1f, 0f});
        Long close = candidate("Close Match", "close@example.com", new float[] {0.9f, 0.1f, 0f});
        Long exact = candidate("Exact Match", "exact@example.com", new float[] {2f, 0f, 0f});

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.candidatesConsidered", is(3)))
                .andExpect(jsonPath("$.matches", hasSize(3)))
                .andExpect(jsonPath("$.matches[0].rank", is(1)))
                .andExpect(jsonPath("$.matches[0].candidateId", is(exact.intValue())))
                .andExpect(jsonPath("$.matches[0].fullName", is("Exact Match")))
                .andExpect(jsonPath("$.matches[0].headline", is("Backend Engineer")))
                .andExpect(jsonPath("$.matches[0].score", closeTo(1.0, 0.0001)))
                .andExpect(jsonPath("$.matches[1].candidateId", is(close.intValue())))
                .andExpect(jsonPath("$.matches[1].score", closeTo(0.9939, 0.0001)))
                .andExpect(jsonPath("$.matches[2].candidateId", is(far.intValue())))
                .andExpect(jsonPath("$.matches[2].score", closeTo(0.0, 0.0001)));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void limitsTheNumberOfMatches() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        candidate("First", "first@example.com", new float[] {1f, 0f});
        candidate("Second", "second@example.com", new float[] {1f, 1f});

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatesConsidered", is(2)))
                .andExpect(jsonPath("$.matches", hasSize(1)))
                .andExpect(jsonPath("$.matches[0].fullName", is("First")));
    }

    @Test
    void skipsCandidatesWhoseVectorsCannotBeCompared() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        candidate("Other Model", "other@example.com", new float[] {1f, 0f, 0f});
        candidate("Zero Vector", "zero@example.com", new float[] {0f, 0f});
        candidate("Not Embedded", "none@example.com", null);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatesConsidered", is(0)))
                .andExpect(jsonPath("$.matches", hasSize(0)));
    }

    @Test
    void rejectsAJobWithoutAnEmbedding() throws Exception {
        Long jobId = job(null);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsALimitOutOfRange() throws Exception {
        Long jobId = job(new float[] {1f});

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches").param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/9999/matches")).andExpect(status().isNotFound());
    }

    @Test
    void cosineSimilarityHandlesOppositeAndMismatchedVectors() {
        assertThat(MatchingService.cosineSimilarity(new float[] {1f, 2f}, new float[] {-1f, -2f}))
                .isCloseTo(-1.0, within(1e-9));
        assertThat(MatchingService.cosineSimilarity(new float[] {1f}, new float[] {1f, 2f})).isNull();
        assertThat(MatchingService.cosineSimilarity(new float[0], new float[0])).isNull();
    }

    private Long job(float[] vector) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        Long id = jobRepository.save(job).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.JOB, id, vector, Instant.now());
        }
        return id;
    }

    private Long candidate(String name, String email, float[] vector) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText("Java and Spring Boot");
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java"), Instant.now());
        Long id = candidateRepository.save(candidate).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.CANDIDATE, id, vector, Instant.now());
        }
        return id;
    }
}
