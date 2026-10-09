package com.talentlens.job;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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

    @Test
    void listsJobsOnePageAtATimeInTheRequestedOrder() throws Exception {
        save("Backend Engineer");
        save("Android Engineer");
        save("Data Engineer");

        mockMvc.perform(get("/api/v1/jobs").param("size", "2").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Android Engineer", "Backend Engineer")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));

        mockMvc.perform(get("/api/v1/jobs").param("page", "1").param("size", "2").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Data Engineer")));
    }

    @Test
    void listsJobsWithDefaultPaging() throws Exception {
        save("Backend Engineer");

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size", is(20)));
    }

    @Test
    void rejectsInvalidPagingParameters() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("sort", "description")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("sort", "title,sideways")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("page", "first")).andExpect(status().isBadRequest());
    }

    @Test
    void filtersJobsByStatusAndLocation() throws Exception {
        save("Backend Engineer", JobStatus.OPEN, "Bengaluru, India");
        save("Data Engineer", JobStatus.CLOSED, "Bengaluru, India");
        save("Android Engineer", JobStatus.OPEN, "Pune");
        save("Remote Engineer", JobStatus.OPEN, null);

        mockMvc.perform(get("/api/v1/jobs").param("status", "OPEN").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title",
                        contains("Android Engineer", "Backend Engineer", "Remote Engineer")));

        mockMvc.perform(get("/api/v1/jobs").param("location", "bengaluru").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Backend Engineer", "Data Engineer")));

        mockMvc.perform(get("/api/v1/jobs").param("status", "OPEN").param("location", " BENGALURU "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Backend Engineer")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void treatsWildcardsInTheLocationFilterLiterally() throws Exception {
        save("Backend Engineer", JobStatus.OPEN, "Pune");

        mockMvc.perform(get("/api/v1/jobs").param("location", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void rejectsUnknownJobStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("status", "ARCHIVED")).andExpect(status().isBadRequest());
    }

    private Long save(String title, JobStatus status, String location) {
        Job job = new Job();
        job.setTitle(title);
        job.setDescription("Description of " + title);
        job.setStatus(status);
        job.setLocation(location);
        return repository.save(job).getId();
    }

    private Long save(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setDescription("Description of " + title);
        return repository.save(job).getId();
    }
}
