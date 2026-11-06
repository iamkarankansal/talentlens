package com.talentlens.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ApplicationHistoryApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationStatusChangeRepository statusChangeRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void clean() {
        statusChangeRepository.deleteAll();
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void recordsEveryStageChangeInOrder() throws Exception {
        Long id = apply();
        mockMvc.perform(changeStatus(id, "SCREENING")).andExpect(status().isOk());
        mockMvc.perform(changeStatus(id, "INTERVIEW")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].fromStatus", nullValue()))
                .andExpect(jsonPath("$[*].toStatus", contains("APPLIED", "SCREENING", "INTERVIEW")))
                .andExpect(jsonPath("$[1].fromStatus", is("APPLIED")))
                .andExpect(jsonPath("$[2].fromStatus", is("SCREENING")))
                .andExpect(jsonPath("$[2].changedAt", notNullValue()));
    }

    @Test
    void rejectedChangesAreNotRecorded() throws Exception {
        Long id = apply();
        mockMvc.perform(changeStatus(id, "OFFER")).andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/applications/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].toStatus", contains("APPLIED")));
    }

    @Test
    void deletingTheApplicationDeletesItsHistory() throws Exception {
        Long id = apply();
        mockMvc.perform(changeStatus(id, "SCREENING")).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/applications/" + id)).andExpect(status().isNoContent());

        assertThat(statusChangeRepository.findByApplicationIdOrderByIdAsc(id)).isEmpty();
        mockMvc.perform(get("/api/v1/applications/" + id + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForAnUnknownApplication() throws Exception {
        mockMvc.perform(get("/api/v1/applications/999999/history")).andExpect(status().isNotFound());
    }

    private Long apply() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha.verma@example.com");
        candidate = candidateRepository.save(candidate);

        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job = jobRepository.save(job);

        String body = mockMvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":" + candidate.getId() + ",\"jobId\":" + job.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private RequestBuilder changeStatus(Long id, String status) {
        return put("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }
}
