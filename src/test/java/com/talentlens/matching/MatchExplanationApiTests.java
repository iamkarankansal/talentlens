package com.talentlens.matching;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.talentlens.ai.MatchExplainer;
import com.talentlens.ai.MatchExplanation;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MatchExplanationApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private MatchExplainer matchExplainer;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void explainsTheMatchWithStrengthsGapsAndScore() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Asha Rao", "asha@example.com", "Java and Spring Boot for five years");
        embeddingStore.save(EmbeddingTarget.JOB, jobId, new float[] {1f, 0f}, Instant.now());
        embeddingStore.save(EmbeddingTarget.CANDIDATE, candidateId, new float[] {1f, 1f}, Instant.now());
        when(matchExplainer.explain(anyString(), anyString())).thenReturn(new MatchExplanation(
                "Strong backend fit, light on Kubernetes.",
                List.of("Five years of Java and Spring Boot"),
                List.of("No Kubernetes experience")));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.candidateId", is(candidateId.intValue())))
                .andExpect(jsonPath("$.fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.score", closeTo(0.7071, 0.0001)))
                .andExpect(jsonPath("$.summary", is("Strong backend fit, light on Kubernetes.")))
                .andExpect(jsonPath("$.strengths", contains("Five years of Java and Spring Boot")))
                .andExpect(jsonPath("$.gaps", contains("No Kubernetes experience")));

        verify(matchExplainer).explain(ArgumentMatchers.contains("Title: Backend Engineer"),
                ArgumentMatchers.contains("Skills: Java, Spring Boot"));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void leavesTheScoreEmptyWithoutEmbeddingsAndToleratesMissingLists() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Ben Lee", "ben@example.com", "Python developer");
        when(matchExplainer.explain(anyString(), anyString()))
                .thenReturn(new MatchExplanation("Weak fit.", null, Arrays.asList("Java", null)));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", nullValue()))
                .andExpect(jsonPath("$.strengths", empty()))
                .andExpect(jsonPath("$.gaps", contains("Java")));
    }

    @Test
    void rejectsACandidateWithNothingToCompare() throws Exception {
        Long jobId = job();
        Candidate candidate = new Candidate();
        candidate.setFullName("Empty Profile");
        candidate.setEmail("empty@example.com");
        Long candidateId = candidateRepository.save(candidate).getId();

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/explanation"))
                .andExpect(status().isConflict());
        verifyNoInteractions(matchExplainer);
    }

    @Test
    void returnsServiceUnavailableWhenTheModelFails() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Cara Diaz", "cara@example.com", "Go developer");
        when(matchExplainer.explain(anyString(), anyString()))
                .thenThrow(new AiUnavailableException("Match explanations are unavailable right now", null));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/explanation"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsNotFoundForUnknownJobOrCandidate() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Dev Shah", "dev@example.com", "Java developer");

        mockMvc.perform(get("/api/v1/jobs/9999/matches/" + candidateId + "/explanation"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/9999/explanation"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(matchExplainer);
    }

    private Long job() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java, Spring Boot and Kubernetes");
        return jobRepository.save(job).getId();
    }

    private Long candidate(String name, String email, String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText(resume);
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java", "Spring Boot"), Instant.now());
        return candidateRepository.save(candidate).getId();
    }
}
