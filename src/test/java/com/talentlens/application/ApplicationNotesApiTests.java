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
class ApplicationNotesApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationNoteRepository noteRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void clean() {
        noteRepository.deleteAll();
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void addsNotesAndListsThemOldestFirst() throws Exception {
        Long id = apply();

        mockMvc.perform(addNote(id, "{\"body\":\"  Strong Kafka background  \",\"author\":\"Priya\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.applicationId", is(id.intValue())))
                .andExpect(jsonPath("$.author", is("Priya")))
                .andExpect(jsonPath("$.body", is("Strong Kafka background")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
        mockMvc.perform(addNote(id, "{\"body\":\"Asked for a later start date\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author", nullValue()));

        mockMvc.perform(get("/api/v1/applications/" + id + "/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].body", contains("Strong Kafka background", "Asked for a later start date")));
    }

    @Test
    void rejectsBlankAndOverlongNotes() throws Exception {
        Long id = apply();

        mockMvc.perform(addNote(id, "{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body", is("body is required")));
        mockMvc.perform(addNote(id, "{\"body\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body", is("body must be at most 2000 characters")));
        mockMvc.perform(addNote(id, "{\"body\":\"ok\",\"author\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.author", is("author must be at most 100 characters")));

        mockMvc.perform(get("/api/v1/applications/" + id + "/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deletingTheApplicationDeletesItsNotes() throws Exception {
        Long id = apply();
        mockMvc.perform(addNote(id, "{\"body\":\"Good culture fit\"}")).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/applications/" + id)).andExpect(status().isNoContent());

        assertThat(noteRepository.findByApplicationIdOrderByIdAsc(id)).isEmpty();
        mockMvc.perform(get("/api/v1/applications/" + id + "/notes")).andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForAnUnknownApplication() throws Exception {
        mockMvc.perform(addNote(999999L, "{\"body\":\"Hello\"}")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/applications/999999/notes")).andExpect(status().isNotFound());
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

    private RequestBuilder addNote(Long id, String json) {
        return post("/api/v1/applications/" + id + "/notes").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
