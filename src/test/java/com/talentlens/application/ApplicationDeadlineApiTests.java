package com.talentlens.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.job.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationDeadlineApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void clean() {
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void acceptsApplicationsBeforeTheDeadline() throws Exception {
        Long jobId = job(Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(apply(candidate("asha@example.com"), jobId)).andExpect(status().isCreated());
    }

    @Test
    void rejectsApplicationsAfterTheDeadline() throws Exception {
        Long jobId = job(Instant.now().minus(Duration.ofHours(1)));

        mockMvc.perform(apply(candidate("asha@example.com"), jobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Job " + jobId + " stopped taking applications at ")));

        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    void jobsWithoutADeadlineKeepTakingApplications() throws Exception {
        mockMvc.perform(apply(candidate("asha@example.com"), job(null))).andExpect(status().isCreated());
    }

    @Test
    void theDeadlineIsSetReadAndClearedThroughTheJobsApi() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java","closesAt":"2030-01-31T18:30:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closesAt", is("2030-01-31T18:30:00Z")));
        Long id = jobRepository.findAll().get(0).getId();
        assertThat(jobRepository.findById(id).orElseThrow().getClosesAt())
                .isEqualTo(Instant.parse("2030-01-31T18:30:00Z"));

        mockMvc.perform(put("/api/v1/jobs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closesAt", nullValue()));
    }

    @Test
    void theDeadlineIsReachedAtTheExactMoment() {
        Instant deadline = Instant.parse("2030-01-31T18:30:00Z");
        Job job = new Job();
        job.setClosesAt(deadline);

        assertThat(job.isPastDeadline(deadline.minusMillis(1))).isFalse();
        assertThat(job.isPastDeadline(deadline)).isTrue();
        assertThat(new Job().isPastDeadline(deadline)).isFalse();
    }

    private Long candidate(String email) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail(email);
        return candidateRepository.save(candidate).getId();
    }

    private Long job(Instant closesAt) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job.setStatus(JobStatus.OPEN);
        job.setClosesAt(closesAt);
        return jobRepository.save(job).getId();
    }

    private RequestBuilder apply(Long candidateId, Long jobId) {
        return post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateId\":" + candidateId + ",\"jobId\":" + jobId + "}");
    }
}
