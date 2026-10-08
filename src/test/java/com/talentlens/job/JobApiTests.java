package com.talentlens.job;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JobApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsJobWithOpenStatusByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java and Spring Boot","minExperienceYears":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Backend Engineer")))
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    @Test
    void rejectsJobWithoutTitle() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Java and Spring Boot"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title", is("title is required")));
    }

    @Test
    void updatesAndDeletesJob() throws Exception {
        Job job = new Job();
        job.setTitle("Old title");
        job.setDescription("Old description");
        Long id = repository.save(job).getId();

        mockMvc.perform(put("/api/v1/jobs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New title","description":"New description","status":"CLOSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("New title")))
                .andExpect(jsonPath("$.status", is("CLOSED")));

        mockMvc.perform(delete("/api/v1/jobs/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/jobs/" + id)).andExpect(status().isNotFound());
    }
}
