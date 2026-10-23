package com.talentlens.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.talentlens.application.Application;
import com.talentlens.application.ApplicationRepository;
import com.talentlens.application.ApplicationStatus;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class EmailDraftApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobRepository jobRepository;

    @MockitoBean
    private EmailDraftAgent agent;

    @BeforeEach
    void clean() {
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void draftsANextRoundEmailAfterTheAgentReadsTheApplication() throws Exception {
        Long applicationId = application(ApplicationStatus.SCREENING);
        List<String> seen = new ArrayList<>();
        when(agent.draft(eq(EmailType.NEXT_ROUND), eq("Mention the panel is remote"), any(EmailDraftTools.class)))
                .thenAnswer(invocation -> {
                    EmailDraftTools tools = invocation.getArgument(2);
                    seen.add(tools.applicationDetails());
                    seen.add(tools.candidateProfile());
                    return new EmailDraft("Next steps for Backend Engineer", "  Hi Asha, ...  ");
                });

        request("{\"applicationId\":" + applicationId
                + ",\"type\":\"NEXT_ROUND\",\"note\":\"Mention the panel is remote\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId", is(applicationId.intValue())))
                .andExpect(jsonPath("$.type", is("NEXT_ROUND")))
                .andExpect(jsonPath("$.to", is("asha.rao@example.com")))
                .andExpect(jsonPath("$.candidateName", is("Asha Rao")))
                .andExpect(jsonPath("$.jobTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.nextStage", is("INTERVIEW")))
                .andExpect(jsonPath("$.subject", is("Next steps for Backend Engineer")))
                .andExpect(jsonPath("$.body", is("Hi Asha, ...")))
                .andExpect(jsonPath("$.toolCalls", contains("applicationDetails", "candidateProfile")));

        assertThat(seen.get(0)).contains("Asha Rao").contains("Current stage: SCREENING")
                .contains("Invited to stage: INTERVIEW").contains("Java and Spring Boot");
        assertThat(seen.get(1)).contains("Java");
    }

    @Test
    void draftsARejectionWithoutANextStage() throws Exception {
        Long applicationId = application(ApplicationStatus.REJECTED);
        when(agent.draft(eq(EmailType.REJECTION), isNull(), any(EmailDraftTools.class)))
                .thenReturn(new EmailDraft("Your application", "Thank you for applying."));

        request("{\"applicationId\":" + applicationId + ",\"type\":\"REJECTION\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStage", nullValue()))
                .andExpect(jsonPath("$.body", is("Thank you for applying.")))
                .andExpect(jsonPath("$.toolCalls", hasSize(0)));
    }

    @Test
    void exposesTheToolsToTheModel() {
        EmailDraftTools tools = new EmailDraftTools("Candidate: Eli Park", "Headline: Engineer");
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build()
                .getToolCallbacks();
        List<String> names = Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("applicationDetails", "candidateProfile");
    }

    @Test
    void validatesTheRequestAndTheApplicationStage() throws Exception {
        Long offer = application(ApplicationStatus.OFFER);
        Long hired = applicationFor("Ben Lee", ApplicationStatus.HIRED);

        request("{}").andExpect(status().isBadRequest());
        request("{\"applicationId\":" + offer + "}").andExpect(status().isBadRequest());
        request("{\"applicationId\":" + offer + ",\"type\":\"REJECTION\",\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        request("{\"applicationId\":9999,\"type\":\"REJECTION\"}").andExpect(status().isNotFound());
        request("{\"applicationId\":" + offer + ",\"type\":\"NEXT_ROUND\"}").andExpect(status().isConflict());
        request("{\"applicationId\":" + hired + ",\"type\":\"REJECTION\"}").andExpect(status().isConflict());
        verifyNoInteractions(agent);
    }

    @Test
    void returnsServiceUnavailableWhenTheAgentFailsOrReturnsNoBody() throws Exception {
        Long applicationId = application(ApplicationStatus.APPLIED);
        when(agent.draft(eq(EmailType.REJECTION), any(), any(EmailDraftTools.class)))
                .thenThrow(new AiUnavailableException("The email drafting agent is unavailable right now", null));
        when(agent.draft(eq(EmailType.NEXT_ROUND), any(), any(EmailDraftTools.class)))
                .thenReturn(new EmailDraft("Subject", " "));

        request("{\"applicationId\":" + applicationId + ",\"type\":\"REJECTION\"}")
                .andExpect(status().isServiceUnavailable());
        request("{\"applicationId\":" + applicationId + ",\"type\":\"NEXT_ROUND\"}")
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void nextStageFollowsThePipeline() {
        assertThat(EmailDraftService.nextStage(ApplicationStatus.APPLIED)).isEqualTo(ApplicationStatus.SCREENING);
        assertThat(EmailDraftService.nextStage(ApplicationStatus.INTERVIEW)).isEqualTo(ApplicationStatus.OFFER);
        assertThat(EmailDraftService.nextStage(ApplicationStatus.OFFER)).isNull();
        assertThat(EmailDraftService.nextStage(ApplicationStatus.REJECTED)).isNull();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/emails")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long application(ApplicationStatus status) {
        return applicationFor("Asha Rao", status);
    }

    private Long applicationFor(String name, ApplicationStatus status) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job = jobRepository.save(job);

        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        candidate.setResumeText(name + " builds Java services");
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java", "Spring Boot"), Instant.now());
        candidate = candidateRepository.save(candidate);

        Application application = new Application(candidate, job);
        application.setStatus(status);
        return applicationRepository.save(application).getId();
    }
}
