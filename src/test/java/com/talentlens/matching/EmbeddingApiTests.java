package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EmbeddingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void embedsAJobAndStoresTheVector() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {0.25f, -0.5f, 1.0f});
        Long id = job();

        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target", is("JOB")))
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.dimensions", is(3)))
                .andExpect(jsonPath("$.embeddedAt", notNullValue()));

        assertThat(embeddingStore.find(EmbeddingTarget.JOB, id)).hasValueSatisfying(
                vector -> assertThat(vector).containsExactly(0.25f, -0.5f, 1.0f));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(embeddingModel).embed(text.capture());
        assertThat(text.getValue()).contains("Title: Backend Engineer").contains("Description: Build APIs");
    }

    @Test
    void embedsTheCandidateProfileAndResume() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {0.1f, 0.2f});
        Long id = candidate("Five years of Java", List.of("Java", "Kafka"));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target", is("CANDIDATE")))
                .andExpect(jsonPath("$.dimensions", is(2)));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(embeddingModel).embed(text.capture());
        assertThat(text.getValue()).contains("Skills: Java, Kafka").contains("Resume: Five years of Java");
        assertThat(embeddingStore.find(EmbeddingTarget.CANDIDATE, id)).isPresent();
    }

    @Test
    void reEmbeddingReplacesTheStoredVector() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {1f, 2f}, new float[] {3f, 4f, 5f});
        Long id = job();

        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(jsonPath("$.dimensions", is(3)));

        assertThat(jdbcTemplate.queryForObject("select count(*) from job_embeddings", Integer.class)).isEqualTo(1);
        assertThat(embeddingStore.find(EmbeddingTarget.JOB, id))
                .hasValueSatisfying(vector -> assertThat(vector).containsExactly(3f, 4f, 5f));
    }

    @Test
    void deletingAJobDeletesItsEmbedding() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {1f});
        Long id = job();
        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(status().isOk());

        jobRepository.deleteById(id);

        assertThat(embeddingStore.find(EmbeddingTarget.JOB, id)).isEmpty();
    }

    @Test
    void rejectsACandidateWithNothingToEmbed() throws Exception {
        Long id = candidate(null, List.of());

        mockMvc.perform(post("/api/v1/candidates/" + id + "/embedding"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/9999/embedding")).andExpect(status().isNotFound());
    }

    @Test
    void returnsServiceUnavailableWhenTheModelFails() throws Exception {
        given(embeddingModel.embed(anyString())).willThrow(new IllegalStateException("model down"));
        Long id = job();

        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", is("Embeddings are unavailable right now")));
        assertThat(embeddingStore.find(EmbeddingTarget.JOB, id)).isEmpty();
    }

    @Test
    void vectorTextRoundTrips() {
        float[] vector = {0.000123f, -2.5f, 3f};

        assertThat(EmbeddingStore.fromText(EmbeddingStore.toText(vector))).containsExactly(vector);
    }

    private Long job() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        job.setLocation("Bengaluru");
        return jobRepository.save(job).getId();
    }

    private Long candidate(String resume, List<String> skills) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha@example.com");
        candidate.setResumeText(resume);
        if (!skills.isEmpty()) {
            candidate.setProfile("Backend Engineer", 5.0, skills, Instant.now());
        }
        return candidateRepository.save(candidate).getId();
    }
}
