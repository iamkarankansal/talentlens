package com.talentlens.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class JobLifecycleApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void publishesClosesAndReopensAJob() throws Exception {
        Long id = save(JobStatus.DRAFT);

        changeStatus(id, "OPEN").andExpect(status().isOk()).andExpect(jsonPath("$.status", is("OPEN")));
        changeStatus(id, "CLOSED").andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CLOSED")));
        changeStatus(id, "OPEN").andExpect(status().isOk()).andExpect(jsonPath("$.status", is("OPEN")));

        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.OPEN);
    }

    @Test
    void rejectsMovesOutsideTheLifecycle() throws Exception {
        Long open = save(JobStatus.OPEN);
        changeStatus(open, "DRAFT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Cannot move job " + open + " from OPEN to DRAFT")));

        Long draft = save(JobStatus.DRAFT);
        changeStatus(draft, "CLOSED").andExpect(status().isConflict());

        assertThat(repository.findById(draft).orElseThrow().getStatus()).isEqualTo(JobStatus.DRAFT);
    }

    @Test
    void keepingTheSameStatusIsAllowed() throws Exception {
        Long id = save(JobStatus.CLOSED);

        changeStatus(id, "CLOSED").andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CLOSED")));
    }

    @Test
    void validatesTheStatusRequest() throws Exception {
        Long id = save(JobStatus.OPEN);

        mockMvc.perform(put("/api/v1/jobs/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status", is("status is required")));
        changeStatus(id, "ARCHIVED").andExpect(status().isBadRequest());
        changeStatus(999_999L, "OPEN").andExpect(status().isNotFound());
    }

    @Test
    void fullUpdatesFollowTheSameLifecycle() throws Exception {
        Long id = save(JobStatus.OPEN);

        mockMvc.perform(put("/api/v1/jobs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java","status":"DRAFT"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/jobs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    @Test
    void aJobCanStartAsADraft() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java","status":"DRAFT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    void statusRulesMatchTheLifecycle() {
        assertThat(JobStatus.DRAFT.canMoveTo(JobStatus.OPEN)).isTrue();
        assertThat(JobStatus.DRAFT.canMoveTo(JobStatus.CLOSED)).isFalse();
        assertThat(JobStatus.OPEN.canMoveTo(JobStatus.CLOSED)).isTrue();
        assertThat(JobStatus.OPEN.canMoveTo(JobStatus.DRAFT)).isFalse();
        assertThat(JobStatus.CLOSED.canMoveTo(JobStatus.OPEN)).isTrue();
        assertThat(JobStatus.CLOSED.canMoveTo(JobStatus.DRAFT)).isFalse();
        assertThat(JobStatus.OPEN.canMoveTo(JobStatus.OPEN)).isFalse();
    }

    private ResultActions changeStatus(Long id, String status) throws Exception {
        return mockMvc.perform(put("/api/v1/jobs/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private Long save(JobStatus status) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job.setStatus(status);
        return repository.save(job).getId();
    }
}
