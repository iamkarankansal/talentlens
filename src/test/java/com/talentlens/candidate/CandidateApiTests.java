package com.talentlens.candidate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void storesTheParsedProfileOnTheCandidate() throws Exception {
        Long id = save("Asha Verma", "asha@example.com", "5 years of Java, Spring Boot and Kafka");
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(
                "  Backend Engineer ", 5.5, List.of("Java", " java ", "Kafka", " "), List.of(), "Backend engineer"));

        mockMvc.perform(get("/api/v1/candidates/" + id))
                .andExpect(jsonPath("$.headline", nullValue()))
                .andExpect(jsonPath("$.skills", empty()))
                .andExpect(jsonPath("$.profileParsedAt", nullValue()));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/candidates/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline", is("Backend Engineer")))
                .andExpect(jsonPath("$.experienceYears", is(5.5)))
                .andExpect(jsonPath("$.skills", contains("Java", "Kafka")))
                .andExpect(jsonPath("$.profileParsedAt", notNullValue()));
    }

    @Test
    void ignoresNegativeExperienceAndMissingSkills() throws Exception {
        Long id = save("Asha Verma", "asha@example.com", "Some resume");
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(null, -2.0, null, null, null));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/candidates/" + id))
                .andExpect(jsonPath("$.experienceYears", nullValue()))
                .andExpect(jsonPath("$.skills", empty()))
                .andExpect(jsonPath("$.profileParsedAt", notNullValue()));
    }

    @Test
    void clearsTheProfileWhenTheResumeChanges() throws Exception {
        Long id = save("Asha Verma", "asha@example.com", "5 years of Java");
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(
                "Backend Engineer", 5.0, List.of("Java"), List.of(), null));
        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse")).andExpect(status().isOk());

        // Same resume: the profile stays.
        mockMvc.perform(put("/api/v1/candidates/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asha V","email":"asha@example.com","resumeText":"5 years of Java"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", contains("Java")));

        // New resume: the old profile no longer applies.
        mockMvc.perform(put("/api/v1/candidates/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asha V","email":"asha@example.com","resumeText":"8 years of Go"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline", nullValue()))
                .andExpect(jsonPath("$.skills", empty()))
                .andExpect(jsonPath("$.profileParsedAt", nullValue()));
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

    @Test
    void searchesCandidatesByNameOrEmail() throws Exception {
        save("Asha Verma", "asha@example.com", null);
        save("Rohan Mehta", "rohan@verma-labs.com", null);
        save("Meera Nair", "meera@example.com", null);

        mockMvc.perform(get("/api/v1/candidates").param("q", "VERMA").param("sort", "fullName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName", contains("Asha Verma", "Rohan Mehta")))
                .andExpect(jsonPath("$.totalElements", is(2)));

        mockMvc.perform(get("/api/v1/candidates").param("q", "meera@"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName", contains("Meera Nair")));

        mockMvc.perform(get("/api/v1/candidates").param("q", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(get("/api/v1/candidates").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    private Long save(String name, String email, String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText(resume);
        return repository.save(candidate).getId();
    }
}
