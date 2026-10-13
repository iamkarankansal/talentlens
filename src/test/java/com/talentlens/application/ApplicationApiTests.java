package com.talentlens.application;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ApplicationApiTests {

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
    void createsApplicationInTheAppliedStage() throws Exception {
        Long candidateId = candidate("asha@example.com");
        Long jobId = job(JobStatus.OPEN);

        mockMvc.perform(apply(candidateId, jobId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.candidateId", is(candidateId.intValue())))
                .andExpect(jsonPath("$.candidateName", is("Asha Verma")))
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.jobTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    void readsAndDeletesApplication() throws Exception {
        Long id = applicationId(candidate("asha@example.com"), job(JobStatus.OPEN));

        mockMvc.perform(get("/api/v1/applications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")));

        mockMvc.perform(delete("/api/v1/applications/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/applications/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsSecondApplicationToTheSameJob() throws Exception {
        Long candidateId = candidate("asha@example.com");
        Long jobId = job(JobStatus.OPEN);
        mockMvc.perform(apply(candidateId, jobId)).andExpect(status().isCreated());

        mockMvc.perform(apply(candidateId, jobId)).andExpect(status().isConflict());
    }

    @Test
    void rejectsApplicationToAJobThatIsNotOpen() throws Exception {
        mockMvc.perform(apply(candidate("asha@example.com"), job(JobStatus.CLOSED)))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownCandidateOrJob() throws Exception {
        Long candidateId = candidate("asha@example.com");
        Long jobId = job(JobStatus.OPEN);

        mockMvc.perform(apply(candidateId + 1000, jobId)).andExpect(status().isNotFound());
        mockMvc.perform(apply(candidateId, jobId + 1000)).andExpect(status().isNotFound());
    }

    @Test
    void requiresCandidateAndJob() throws Exception {
        mockMvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.candidateId", is("candidateId is required")))
                .andExpect(jsonPath("$.fieldErrors.jobId", is("jobId is required")));
    }

    @Test
    void deletingTheCandidateRemovesTheirApplications() throws Exception {
        Long candidateId = candidate("asha@example.com");
        Long id = applicationId(candidateId, job(JobStatus.OPEN));

        mockMvc.perform(delete("/api/v1/candidates/" + candidateId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/applications/" + id)).andExpect(status().isNotFound());
    }

    private Long candidate(String email) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail(email);
        return candidateRepository.save(candidate).getId();
    }

    private Long job(JobStatus status) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job.setStatus(status);
        return jobRepository.save(job).getId();
    }

    private Long applicationId(Long candidateId, Long jobId) {
        Application application = new Application(candidateRepository.findById(candidateId).orElseThrow(),
                jobRepository.findById(jobId).orElseThrow());
        return applicationRepository.save(application).getId();
    }

    private RequestBuilder apply(Long candidateId, Long jobId) {
        return post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateId\":" + candidateId + ",\"jobId\":" + jobId + "}");
    }
}
