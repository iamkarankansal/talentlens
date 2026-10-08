package com.talentlens.candidate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.talentlens.ai.ResumeParser;
import com.talentlens.ai.ResumeProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CandidateApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateRepository repository;

    @MockitoBean
    private ResumeParser resumeParser;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsCandidateAndNormalizesEmail() throws Exception {
        mockMvc.perform(post("/api/v1/candidates").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asha Verma","email":"Asha.Verma@Example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("asha.verma@example.com")))
                .andExpect(jsonPath("$.hasResume", is(false)));
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() throws Exception {
        save("Asha Verma", "asha@example.com", null);

        mockMvc.perform(post("/api/v1/candidates").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Another Asha","email":"ASHA@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownCandidate() throws Exception {
        mockMvc.perform(get("/api/v1/candidates/9999")).andExpect(status().isNotFound());
    }

    @Test
    void parsesResumeThroughTheParser() throws Exception {
        Long id = save("Asha Verma", "asha@example.com", "5 years of Java, Spring Boot and Kafka");
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(
                "Backend Engineer", 5.0, List.of("Java", "Spring Boot", "Kafka"), List.of(), "Backend engineer"));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExperienceYears", is(5.0)))
                .andExpect(jsonPath("$.skills", containsInAnyOrder("Java", "Spring Boot", "Kafka")));
    }

    @Test
    void refusesToParseWhenResumeIsMissing() throws Exception {
        Long id = save("Asha Verma", "asha@example.com", null);

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse"))
                .andExpect(status().isConflict());
    }

    @Test
    void listsCandidatesPagedAndSorted() throws Exception {
        save("Asha Verma", "asha@example.com", null);
        save("Rohan Mehta", "rohan@example.com", null);
        save("Meera Nair", "meera@example.com", null);

        mockMvc.perform(get("/api/v1/candidates").param("size", "2").param("sort", "fullName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName", contains("Rohan Mehta", "Meera Nair")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void rejectsSortingCandidatesByAnUnknownField() throws Exception {
        mockMvc.perform(get("/api/v1/candidates").param("sort", "resumeText"))
                .andExpect(status().isBadRequest());
    }

    private Long save(String name, String email, String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText(resume);
        return repository.save(candidate).getId();
    }
}
