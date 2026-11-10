package com.talentlens.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentlens.application.Application;
import com.talentlens.application.ApplicationRepository;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JobSoftDeleteApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void clean() {
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void deletedJobsDisappearFromReadsAndListsButKeepTheirRow() throws Exception {
        Long kept = job("Frontend Engineer");
        Long deleted = job("Backend Engineer");

        mockMvc.perform(delete("/api/v1/jobs/" + deleted)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/jobs/" + deleted)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(kept.intValue())))
                .andExpect(jsonPath("$.totalElements", is(1)));
        mockMvc.perform(delete("/api/v1/jobs/" + deleted)).andExpect(status().isNotFound());

        Job row = jobRepository.findById(deleted).orElseThrow();
        assertThat(row.getDeletedAt()).isNotNull();
    }

    @Test
    void deletingAJobKeepsItsApplications() throws Exception {
        Long jobId = job("Backend Engineer");
        Long applicationId = application(jobId);

        mockMvc.perform(delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());

        assertThat(applicationRepository.findById(applicationId)).isPresent();
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications")).andExpect(status().isNotFound());
    }

    @Test
    void restoresADeletedJobWithItsApplications() throws Exception {
        Long jobId = job("Backend Engineer");
        application(jobId);
        mockMvc.perform(delete("/api/v1/jobs/" + jobId)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(jobId.intValue())))
                .andExpect(jsonPath("$.title", is("Backend Engineer")));

        mockMvc.perform(get("/api/v1/jobs/" + jobId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        assertThat(jobRepository.findById(jobId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void restoringAJobThatIsNotDeletedIsAConflict() throws Exception {
        Long jobId = job("Backend Engineer");

        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Job " + jobId + " is not deleted")));
        mockMvc.perform(post("/api/v1/jobs/999999/restore")).andExpect(status().isNotFound());
    }

    private Long job(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setDescription("Java and Spring Boot");
        job.setStatus(JobStatus.OPEN);
        return jobRepository.save(job).getId();
    }

    private Long application(Long jobId) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha@example.com");
        candidate = candidateRepository.save(candidate);
        Application application = new Application(candidate, jobRepository.findById(jobId).orElseThrow());
        return applicationRepository.save(application).getId();
    }
}
