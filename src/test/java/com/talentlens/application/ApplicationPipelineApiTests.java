package com.talentlens.application;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationPipelineApiTests {

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
    void movesApplicationThroughThePipelineToHired() throws Exception {
        Long id = apply(candidate("Asha Verma"), job("Backend Engineer"));

        for (String next : new String[] {"SCREENING", "INTERVIEW", "OFFER", "HIRED"}) {
            mockMvc.perform(changeStatus(id, next))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is(next)));
        }
        mockMvc.perform(get("/api/v1/applications/" + id)).andExpect(jsonPath("$.status", is("HIRED")));
    }

    @Test
    void rejectsSkippingStagesAndChangingFinalStages() throws Exception {
        Long id = apply(candidate("Asha Verma"), job("Backend Engineer"));

        mockMvc.perform(changeStatus(id, "OFFER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("from APPLIED to OFFER")));

        mockMvc.perform(changeStatus(id, "REJECTED")).andExpect(status().isOk());
        mockMvc.perform(changeStatus(id, "SCREENING")).andExpect(status().isConflict());
    }

    @Test
    void requiresAStatusAndAnExistingApplication() throws Exception {
        mockMvc.perform(put("/api/v1/applications/999999/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status", is("status is required")));

        mockMvc.perform(changeStatus(999999L, "SCREENING")).andExpect(status().isNotFound());
    }

    @Test
    void listsApplicationsForAJobFilteredByStatus() throws Exception {
        Job job = job("Backend Engineer");
        Long asha = apply(candidate("Asha Verma"), job);
        apply(candidate("Ravi Kumar"), job);
        apply(candidate("Meera Iyer"), job("Data Engineer"));
        mockMvc.perform(changeStatus(asha, "SCREENING")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/applications").param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].candidateName", contains("Asha Verma", "Ravi Kumar")));

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/applications").param("status", "SCREENING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].candidateName", is("Asha Verma")));
    }

    @Test
    void listsApplicationsForACandidate() throws Exception {
        Candidate asha = candidate("Asha Verma");
        apply(asha, job("Backend Engineer"));
        apply(asha, job("Data Engineer"));
        apply(candidate("Ravi Kumar"), job("Android Engineer"));

        mockMvc.perform(get("/api/v1/candidates/" + asha.getId() + "/applications").param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].jobTitle", contains("Backend Engineer", "Data Engineer")));
    }

    @Test
    void returnsNotFoundWhenListingForAnUnknownJobOrCandidate() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/999999/applications")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/candidates/999999/applications")).andExpect(status().isNotFound());
    }

    private Candidate candidate(String name) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        return candidateRepository.save(candidate);
    }

    private Job job(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setDescription("Java and Spring Boot");
        return jobRepository.save(job);
    }

    private Long apply(Candidate candidate, Job job) {
        return applicationRepository.save(new Application(candidate, job)).getId();
    }

    private RequestBuilder changeStatus(Long id, String status) {
        return put("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }
}
