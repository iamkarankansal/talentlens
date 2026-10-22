package com.talentlens.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import com.talentlens.matching.MatchingService;
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
class ShortlistApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private MatchingService matchingService;

    @MockitoBean
    private ShortlistAgent agent;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void returnsTheAgentsPicksAfterItCallsTheTools() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        Long best = candidate("Asha Rao", new float[] {1f, 0f});
        Long second = candidate("Ben Lee", new float[] {0.6f, 0.8f});
        Long third = candidate("Cara Diaz", new float[] {0f, 1f});
        List<String> profiles = new ArrayList<>();
        when(agent.shortlist(anyString(), eq(2), any(ShortlistTools.class))).thenAnswer(invocation -> {
            ShortlistTools tools = invocation.getArgument(2);
            assertThat(tools.rankCandidates(10)).hasSize(3);
            profiles.add(tools.candidateProfile(best));
            profiles.add(tools.candidateProfile(9999L));
            return new Shortlist("Two strong Java backend candidates.", Arrays.asList(
                    null, new Shortlist.Pick(9999L, "Invented"), new Shortlist.Pick(best, "Closest match"),
                    new Shortlist.Pick(best, "Duplicate"), new Shortlist.Pick(second, "Solid Java"),
                    new Shortlist.Pick(third, "Over the size")));
        });

        request("{\"jobId\":" + jobId + ",\"size\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.jobTitle", is("Backend Engineer")))
                .andExpect(jsonPath("$.summary", is("Two strong Java backend candidates.")))
                .andExpect(jsonPath("$.candidates", hasSize(2)))
                .andExpect(jsonPath("$.candidates[0].rank", is(1)))
                .andExpect(jsonPath("$.candidates[0].candidateId", is(best.intValue())))
                .andExpect(jsonPath("$.candidates[0].fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.candidates[0].headline", is("Backend Engineer")))
                .andExpect(jsonPath("$.candidates[0].score", closeTo(1.0, 0.0001)))
                .andExpect(jsonPath("$.candidates[0].reason", is("Closest match")))
                .andExpect(jsonPath("$.candidates[1].candidateId", is(second.intValue())))
                .andExpect(jsonPath("$.candidates[1].score", closeTo(0.6, 0.0001)))
                .andExpect(jsonPath("$.toolCalls",
                        contains("rankCandidates", "candidateProfile", "candidateProfile")));

        assertThat(profiles.get(0)).contains("Asha Rao").contains("Java");
        assertThat(profiles.get(1)).isEqualTo("No candidate with id 9999");
    }

    @Test
    void scoreIsNullForPicksTheAgentNeverRankedAndDefaultSizeIsFive() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        Long candidateId = candidate("Dev Shah", new float[] {1f, 0f});
        when(agent.shortlist(anyString(), eq(5), any(ShortlistTools.class))).thenReturn(
                new Shortlist(null, List.of(new Shortlist.Pick(candidateId, "Matches the stack"))));

        request("{\"jobId\":" + jobId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.candidates[0].score", nullValue()))
                .andExpect(jsonPath("$.toolCalls", hasSize(0)));
    }

    @Test
    void exposesTheToolsToTheModel() {
        Long jobId = job(new float[] {1f, 0f});
        candidate("Eli Park", new float[] {1f, 0f});
        ShortlistTools tools = new ShortlistTools(jobId, matchingService, candidateRepository);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build()
                .getToolCallbacks();
        List<String> names = Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("rankCandidates", "candidateProfile");

        ToolCallback rank = Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals("rankCandidates"))
                .findFirst().orElseThrow();
        assertThat(rank.call("{\"limit\":5}")).contains("Eli Park");
    }

    @Test
    void validatesTheRequestAndRequiresAJobEmbedding() throws Exception {
        Job unembedded = new Job();
        unembedded.setTitle("Data Engineer");
        unembedded.setDescription("Spark");
        Long unembeddedId = jobRepository.save(unembedded).getId();

        request("{}").andExpect(status().isBadRequest());
        request("{\"jobId\":" + unembeddedId + ",\"size\":0}").andExpect(status().isBadRequest());
        request("{\"jobId\":" + unembeddedId + ",\"size\":21}").andExpect(status().isBadRequest());
        request("{\"jobId\":9999}").andExpect(status().isNotFound());
        request("{\"jobId\":" + unembeddedId + "}").andExpect(status().isConflict());
        verifyNoInteractions(agent);
    }

    @Test
    void returnsServiceUnavailableWhenTheAgentFails() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        when(agent.shortlist(anyString(), anyInt(), any(ShortlistTools.class)))
                .thenThrow(new AiUnavailableException("The shortlisting agent is unavailable right now", null));

        request("{\"jobId\":" + jobId + "}").andExpect(status().isServiceUnavailable());
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/assistant/shortlist")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long job(float[] vector) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        Long id = jobRepository.save(job).getId();
        embeddingStore.save(EmbeddingTarget.JOB, id, vector, Instant.now());
        return id;
    }

    private Long candidate(String name, float[] vector) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        candidate.setResumeText(name + " builds Java services");
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java", "Spring Boot"), Instant.now());
        Long id = candidateRepository.save(candidate).getId();
        embeddingStore.save(EmbeddingTarget.CANDIDATE, id, vector, Instant.now());
        return id;
    }
}
