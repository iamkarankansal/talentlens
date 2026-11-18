package com.talentlens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeParsingEdgeCaseApiTests {

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
    void refusesAResumeTooShortToParseWithoutAskingTheModel() throws Exception {
        Long id = save("   Java dev   ");

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("too short")));
        verifyNoInteractions(resumeParser);
    }

    @Test
    void cutsAnOverlongResumeBeforeTheModelCall() throws Exception {
        String resume = "Senior Java engineer. " + "Built payment APIs with Spring Boot. ".repeat(1000);
        assertThat(resume.length()).isGreaterThan(ResumeParsingRules.MAX_LENGTH);
        Long id = save(resume);
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(
                "Senior Java Engineer", 9.0, List.of("Java"), List.of(), null));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse")).andExpect(status().isOk());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(resumeParser).parse(sent.capture());
        assertThat(sent.getValue()).hasSize(ResumeParsingRules.MAX_LENGTH).startsWith("Senior Java engineer.");
    }

    @Test
    void dropsImplausibleExperienceFromTheResponseAndTheCandidate() throws Exception {
        Long id = save("Backend engineer working with Java since school");
        when(resumeParser.parse(anyString())).thenReturn(new ResumeProfile(
                "Backend Engineer", 85.0, List.of("Java"), List.of(), null));

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline", is("Backend Engineer")))
                .andExpect(jsonPath("$.totalExperienceYears", nullValue()));
        mockMvc.perform(get("/api/v1/candidates/" + id))
                .andExpect(jsonPath("$.experienceYears", nullValue()))
                .andExpect(jsonPath("$.skills[0]", is("Java")));
    }

    @Test
    void storesAnEmptyProfileWhenTheModelReturnsNothing() throws Exception {
        Long id = save("Backend engineer with Java and Kafka experience");
        when(resumeParser.parse(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/v1/candidates/" + id + "/resume/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline", nullValue()))
                .andExpect(jsonPath("$.skills.length()", is(0)));
    }

    private Long save(String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Rao");
        candidate.setEmail("asha@example.com");
        candidate.setResumeText(resume);
        return repository.save(candidate).getId();
    }
}
