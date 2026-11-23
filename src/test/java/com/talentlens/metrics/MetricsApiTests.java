package com.talentlens.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentlens.ai.ModelCallMetrics;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class MetricsApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void prometheusEndpointExposesModelCallTimers() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {0.5f, 1f});
        Long id = job();
        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("talentlens_ai_model_calls_seconds_count{")))
                .andExpect(content().string(containsString("operation=\"embedding\"")))
                .andExpect(content().string(containsString("outcome=\"success\"")))
                .andExpect(content().string(containsString("application=\"talentlens\"")));
    }

    @Test
    void failedModelCallsAreTimedAsFailures() throws Exception {
        given(embeddingModel.embed(anyString())).willThrow(new IllegalStateException("model down"));
        Long id = job();
        double before = failures();

        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(status().isServiceUnavailable());

        assertThat(failures()).isEqualTo(before + 1);
    }

    @Test
    void metricsEndpointDescribesTheModelCallTimer() throws Exception {
        given(embeddingModel.embed(anyString())).willReturn(new float[] {1f});
        Long id = job();
        mockMvc.perform(post("/api/v1/jobs/" + id + "/embedding")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/" + ModelCallMetrics.TIMER).param("tag", "operation:embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(ModelCallMetrics.TIMER)))
                .andExpect(jsonPath("$.baseUnit", is("seconds")));
    }

    private double failures() {
        Timer timer = meterRegistry.find(ModelCallMetrics.TIMER)
                .tag("operation", ModelCallMetrics.EMBEDDING)
                .tag("outcome", "failure")
                .timer();
        return timer == null ? 0 : timer.count();
    }

    private Long job() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        job.setLocation("Bengaluru");
        return jobRepository.save(job).getId();
    }
}
