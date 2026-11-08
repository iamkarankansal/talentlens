package com.talentlens.application;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationPipelineSummaryApiTests {

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
    void countsApplicationsInEachStageOfTheJob() throws Exception {
        Job job = job("Backend Engineer");
        apply("Asha Verma", job, ApplicationStatus.APPLIED);
        apply("Ravi Kumar", job, ApplicationStatus.SCREENING);
        apply("Meera Iyer", job, ApplicationStatus.SCREENING);
        apply("Arjun Rao", job, ApplicationStatus.REJECTED);
        apply("Neha Singh", job("Data Engineer"), ApplicationStatus.OFFER);

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(job.getId().intValue())))
                .andExpect(jsonPath("$.jobTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.total", is(4)))
                .andExpect(jsonPath("$.stages.APPLIED", is(1)))
                .andExpect(jsonPath("$.stages.SCREENING", is(2)))
                .andExpect(jsonPath("$.stages.INTERVIEW", is(0)))
                .andExpect(jsonPath("$.stages.OFFER", is(0)))
                .andExpect(jsonPath("$.stages.REJECTED", is(1)));
    }

    @Test
    void listsEveryStageInPipelineOrderForAJobWithoutApplications() throws Exception {
        Job job = job("Android Engineer");

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(content().string(containsString("\"stages\":{\"APPLIED\":0,\"SCREENING\":0,"
                        + "\"INTERVIEW\":0,\"OFFER\":0,\"HIRED\":0,\"REJECTED\":0,\"WITHDRAWN\":0}")));
    }

    @Test
    void returnsNotFoundForAnUnknownJob() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/999999/pipeline")).andExpect(status().isNotFound());
    }

    private Job job(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setDescription("Java and Spring Boot");
        return jobRepository.save(job);
    }

    private void apply(String name, Job job, ApplicationStatus stage) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        candidate = candidateRepository.save(candidate);
        Application application = new Application(candidate, job);
        application.setStatus(stage);
        applicationRepository.save(application);
    }
}
