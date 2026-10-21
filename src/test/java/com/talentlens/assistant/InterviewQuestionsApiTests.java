package com.talentlens.assistant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.talentlens.ai.InterviewQuestionGenerator;
import com.talentlens.ai.InterviewQuestions;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewQuestionsApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @MockitoBean
    private InterviewQuestionGenerator generator;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void generatesQuestionsTailoredToTheCandidateAndJob() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Asha Rao", "Java and Spring Boot for five years");
        when(generator.generate(anyString(), anyString(), eq(2))).thenReturn(new InterviewQuestions(List.of(
                new InterviewQuestions.Question("How did you tune Spring Boot services under load?",
                        "Spring Boot", "Verifies the five years of Spring Boot on the resume."),
                new InterviewQuestions.Question("How would you deploy a service to Kubernetes?",
                        "Kubernetes", "The job requires Kubernetes and the profile does not show it."))));

        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + ",\"count\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.candidateId", is(candidateId.intValue())))
                .andExpect(jsonPath("$.fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.jobTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.questions", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].question", is("How did you tune Spring Boot services under load?")))
                .andExpect(jsonPath("$.questions[0].focus", is("Spring Boot")))
                .andExpect(jsonPath("$.questions[1].rationale",
                        is("The job requires Kubernetes and the profile does not show it.")));

        verify(generator).generate(contains("Title: Backend Engineer"), contains("Skills: Java, Spring Boot"), eq(2));
    }

    @Test
    void defaultsToFiveQuestionsAndDropsBlankOnesAndExtras() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Ben Lee", "Python developer");
        List<InterviewQuestions.Question> questions = Arrays.asList(
                question("Q1"), null, new InterviewQuestions.Question(" ", "x", "y"),
                question("Q2"), question("Q3"), question("Q4"), question("Q5"), question("Q6"));
        when(generator.generate(anyString(), anyString(), eq(5))).thenReturn(new InterviewQuestions(questions));

        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[4].question", is("Q5")));
    }

    @Test
    void returnsAnEmptyListWhenTheModelReturnsNothing() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Cara Diaz", "Go developer");
        when(generator.generate(anyString(), anyString(), anyInt())).thenReturn(new InterviewQuestions(null));

        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(0)));
    }

    @Test
    void validatesTheRequest() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Dev Shah", "Java developer");

        request("{\"candidateId\":" + candidateId + "}").andExpect(status().isBadRequest());
        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + ",\"count\":0}")
                .andExpect(status().isBadRequest());
        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + ",\"count\":16}")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generator);
    }

    @Test
    void rejectsUnknownIdsAndCandidatesWithNothingToPrepareFrom() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Eli Park", "Java developer");
        Candidate empty = new Candidate();
        empty.setFullName("Empty Profile");
        empty.setEmail("empty@example.com");
        Long emptyId = candidateRepository.save(empty).getId();

        request("{\"jobId\":9999,\"candidateId\":" + candidateId + "}").andExpect(status().isNotFound());
        request("{\"jobId\":" + jobId + ",\"candidateId\":9999}").andExpect(status().isNotFound());
        request("{\"jobId\":" + jobId + ",\"candidateId\":" + emptyId + "}").andExpect(status().isConflict());
        verifyNoInteractions(generator);
    }

    @Test
    void returnsServiceUnavailableWhenTheModelFails() throws Exception {
        Long jobId = job();
        Long candidateId = candidate("Fay Wong", "Java developer");
        when(generator.generate(anyString(), anyString(), anyInt()))
                .thenThrow(new AiUnavailableException("Interview questions are unavailable right now", null));

        request("{\"jobId\":" + jobId + ",\"candidateId\":" + candidateId + "}")
                .andExpect(status().isServiceUnavailable());
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/interview-questions")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static InterviewQuestions.Question question(String text) {
        return new InterviewQuestions.Question(text, "focus", "rationale");
    }

    private Long job() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java, Spring Boot and Kubernetes");
        return jobRepository.save(job).getId();
    }

    private Long candidate(String name, String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        candidate.setResumeText(resume);
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java", "Spring Boot"), Instant.now());
        return candidateRepository.save(candidate).getId();
    }
}
