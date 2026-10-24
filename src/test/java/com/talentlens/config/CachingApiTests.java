package com.talentlens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.talentlens.ai.MatchExplainer;
import com.talentlens.ai.MatchExplanation;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.cache.type=simple")
@AutoConfigureMockMvc
class CachingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private MatchExplainer matchExplainer;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
        for (String name : List.of(CacheConfig.JOBS, CacheConfig.CANDIDATE_RANKINGS,
                CacheConfig.MATCH_EXPLANATIONS)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    void servesJobsFromTheCacheUntilTheyAreUpdated() throws Exception {
        Long jobId = job();
        mockMvc.perform(get("/api/v1/jobs/" + jobId)).andExpect(jsonPath("$.title", is("Backend Engineer")));

        Job changed = jobRepository.findById(jobId).orElseThrow();
        changed.setTitle("Changed behind the cache");
        jobRepository.save(changed);
        mockMvc.perform(get("/api/v1/jobs/" + jobId)).andExpect(jsonPath("$.title", is("Backend Engineer")));

        mockMvc.perform(put("/api/v1/jobs/" + jobId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Staff Engineer","description":"Build APIs in Java"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + jobId)).andExpect(jsonPath("$.title", is("Staff Engineer")));
    }

    @Test
    void cachesRankingsUntilAnEmbeddingChanges() throws Exception {
        Long jobId = job();
        embeddingStore.save(EmbeddingTarget.JOB, jobId, new float[] {1f, 0f}, Instant.now());
        Long first = candidate("First", "first@example.com");
        embeddingStore.save(EmbeddingTarget.CANDIDATE, first, new float[] {1f, 0f}, Instant.now());

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatesConsidered", is(1)));

        Long second = candidate("Second", "second@example.com");
        embeddingStore.save(EmbeddingTarget.CANDIDATE, second, new float[] {0f, 1f}, Instant.now());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(jsonPath("$.candidatesConsidered", is(1)));

        when(embeddingModel.embed(anyString())).thenReturn(new float[] {1f, 1f});
        mockMvc.perform(post("/api/v1/candidates/" + second + "/embedding")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(jsonPath("$.candidatesConsidered", is(2)));
    }

    @Test
    void asksTheModelOnceForARepeatedExplanationAndCachesASerializableResult() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Asha Rao", "asha@example.com");
        when(matchExplainer.explain(anyString(), anyString())).thenReturn(new MatchExplanation(
                "Strong backend fit.", List.of("Java"), List.of("Kubernetes")));
        String url = "/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/explanation";

        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary", is("Strong backend fit.")));
        verify(matchExplainer, times(1)).explain(anyString(), anyString());

        Object cached = cacheManager.getCache(CacheConfig.MATCH_EXPLANATIONS)
                .get(jobId + ":" + candidateId).get();
        JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
        assertThat(serializer.deserialize(serializer.serialize(cached))).isEqualTo(cached);

        mockMvc.perform(put("/api/v1/jobs/" + jobId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Staff Engineer","description":"Build APIs in Java"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isOk());
        verify(matchExplainer, times(2)).explain(anyString(), anyString());
    }

    private Long job() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        return jobRepository.save(job).getId();
    }

    private Long candidate(String name, String email) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText("Java and Spring Boot");
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java"), Instant.now());
        return candidateRepository.save(candidate).getId();
    }
}
