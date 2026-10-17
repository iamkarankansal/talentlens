package com.talentlens.matching;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.talentlens.ai.SkillExtractor;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SkillGapApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @MockitoBean
    private SkillExtractor skillExtractor;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void reportsMatchedMissingAndAdditionalSkillsIgnoringCaseAndHyphens() throws Exception {
        Long jobId = job(4);
        Long candidateId = candidate("Asha Rao", "asha@example.com", 5.0, List.of("java", "Spring-Boot", "Kafka"));
        when(skillExtractor.requiredSkills(anyString()))
                .thenReturn(Arrays.asList("Java", "Spring Boot", "Kubernetes", "JAVA", " ", null));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.candidateId", is(candidateId.intValue())))
                .andExpect(jsonPath("$.fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.requiredSkills", contains("Java", "Spring Boot", "Kubernetes")))
                .andExpect(jsonPath("$.matchedSkills", contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$.missingSkills", contains("Kubernetes")))
                .andExpect(jsonPath("$.additionalSkills", contains("Kafka")))
                .andExpect(jsonPath("$.coverage", is(0.6667)))
                .andExpect(jsonPath("$.requiredExperienceYears", is(4)))
                .andExpect(jsonPath("$.candidateExperienceYears", is(5.0)))
                .andExpect(jsonPath("$.meetsExperience", is(true)));

        verify(skillExtractor).requiredSkills(ArgumentMatchers.contains("Title: Backend Engineer"));
    }

    @Test
    void leavesCoverageAndExperienceEmptyWhenUnknown() throws Exception {
        Long jobId = job(null);
        Long candidateId = candidate("Ben Lee", "ben@example.com", null, List.of("Python"));
        when(skillExtractor.requiredSkills(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills", empty()))
                .andExpect(jsonPath("$.missingSkills", empty()))
                .andExpect(jsonPath("$.additionalSkills", contains("Python")))
                .andExpect(jsonPath("$.coverage", nullValue()))
                .andExpect(jsonPath("$.meetsExperience", nullValue()));
    }

    @Test
    void flagsTooLittleExperience() throws Exception {
        Long jobId = job(8);
        Long candidateId = candidate("Cara Diaz", "cara@example.com", 3.0, List.of("Java"));
        when(skillExtractor.requiredSkills(anyString())).thenReturn(List.of("Java"));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage", is(1.0)))
                .andExpect(jsonPath("$.meetsExperience", is(false)));
    }

    @Test
    void rejectsACandidateWithoutAParsedProfile() throws Exception {
        Long jobId = job(null);
        Candidate candidate = new Candidate();
        candidate.setFullName("Unparsed");
        candidate.setEmail("unparsed@example.com");
        candidate.setResumeText("Java developer");
        Long candidateId = candidateRepository.save(candidate).getId();

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isConflict());
        verifyNoInteractions(skillExtractor);
    }

    @Test
    void returnsServiceUnavailableWhenTheModelFails() throws Exception {
        Long jobId = job(null);
        Long candidateId = candidate("Dev Shah", "dev@example.com", 2.0, List.of("Go"));
        when(skillExtractor.requiredSkills(anyString()))
                .thenThrow(new AiUnavailableException("Skill extraction is unavailable right now", null));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsNotFoundForUnknownJobOrCandidate() throws Exception {
        Long jobId = job(null);
        Long candidateId = candidate("Eva Ng", "eva@example.com", 1.0, List.of("Java"));

        mockMvc.perform(get("/api/v1/jobs/9999/matches/" + candidateId + "/skill-gap"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/9999/skill-gap"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(skillExtractor);
    }

    private Long job(Integer minExperienceYears) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java, Spring Boot and Kubernetes");
        job.setMinExperienceYears(minExperienceYears);
        return jobRepository.save(job).getId();
    }

    private Long candidate(String name, String email, Double years, List<String> skills) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText("Resume of " + name);
        candidate.setProfile("Engineer", years, skills, Instant.now());
        return candidateRepository.save(candidate).getId();
    }
}
