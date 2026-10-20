package com.talentlens.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import java.util.List;

import com.talentlens.ai.CandidatePoolAssistant;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantChatApiTests {

    private static final String QUESTION = "Who has Kafka experience?";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private CandidatePoolAssistant assistant;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
    }

    @Test
    void answersFromTheClosestCandidatesAndListsThemAsSources() throws Exception {
        Long kafka = candidate("Asha Rao", "asha@example.com", "Kafka streaming", new float[] {1f, 0f});
        Long close = candidate("Ben Lee", "ben@example.com", "RabbitMQ messaging", new float[] {1f, 1f});
        candidate("Cara Diaz", "cara@example.com", "Frontend React", new float[] {0f, 1f});
        when(embeddingModel.embed(QUESTION)).thenReturn(new float[] {1f, 0f});
        when(assistant.answer(anyString(), anyString())).thenReturn(" Asha Rao (12) has Kafka experience. ");

        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + QUESTION + "\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Asha Rao (12) has Kafka experience.")))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[0].candidateId", is(kafka.intValue())))
                .andExpect(jsonPath("$.sources[0].fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.sources[0].headline", is("Backend Engineer")))
                .andExpect(jsonPath("$.sources[0].score", closeTo(1.0, 0.0001)))
                .andExpect(jsonPath("$.sources[1].candidateId", is(close.intValue())))
                .andExpect(jsonPath("$.sources[1].score", closeTo(0.7071, 0.0001)));

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(assistant).answer(eq(QUESTION), context.capture());
        assertThat(context.getValue())
                .contains("[Candidate " + kafka + "] Asha Rao", "Kafka streaming",
                        "[Candidate " + close + "] Ben Lee")
                .doesNotContain("Cara Diaz");
    }

    @Test
    void usesFiveCandidatesByDefault() throws Exception {
        for (int i = 0; i < 6; i++) {
            candidate("Person " + i, "p" + i + "@example.com", "Java", new float[] {1f, i});
        }
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {1f, 0f});
        when(assistant.answer(anyString(), anyString())).thenReturn("Everyone knows Java.");

        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Who knows Java?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(5)))
                .andExpect(jsonPath("$.sources[0].fullName", is("Person 0")));
        verify(assistant).answer(eq("Who knows Java?"), contains("[Candidate"));
    }

    @Test
    void rejectsAChatWhenNoCandidateHasAnEmbedding() throws Exception {
        candidate("Not Embedded", "none@example.com", "Java", null);

        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(embeddingModel, assistant);
    }

    @Test
    void rejectsABlankQuestionAndATopKOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.question", is("question is required")));
        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + QUESTION + "\",\"topK\":21}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(embeddingModel, assistant);
    }

    @Test
    void returnsServiceUnavailableWhenTheModelFails() throws Exception {
        candidate("Asha Rao", "asha@example.com", "Kafka", new float[] {1f, 0f});
        when(embeddingModel.embed(anyString())).thenReturn(new float[] {1f, 0f});
        when(assistant.answer(anyString(), anyString()))
                .thenThrow(new AiUnavailableException("The recruiter assistant is unavailable right now", null));

        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    private Long candidate(String name, String email, String resume, float[] vector) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText(resume);
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java"), Instant.now());
        Long id = candidateRepository.save(candidate).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.CANDIDATE, id, vector, Instant.now());
        }
        return id;
    }
}
