package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "talentlens.ai.rate-limit.requests=0")
@AutoConfigureMockMvc
class BatchEmbeddingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
    }

    @Test
    void embedsOnlyCandidatesWithoutAnEmbeddingAndSkipsEmptyOnes() throws Exception {
        answerWithOneVectorPerText();
        Long fresh = candidate("fresh@example.com", "Five years of Java and Kafka");
        Long alreadyEmbedded = candidate("done@example.com", "Ten years of Go");
        embeddingStore.save(EmbeddingTarget.CANDIDATE, alreadyEmbedded, new float[] {9f, 9f}, Instant.now());
        candidate("empty@example.com", null);

        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded", is(1)))
                .andExpect(jsonPath("$.skipped", is(1)))
                .andExpect(jsonPath("$.candidateIds", contains(fresh.intValue())))
                .andExpect(jsonPath("$.embeddedAt", notNullValue()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embed(texts.capture());
        assertThat(texts.getValue()).hasSize(1);
        assertThat(texts.getValue().get(0)).contains("Resume: Five years of Java and Kafka");
        assertThat(embeddingStore.find(EmbeddingTarget.CANDIDATE, fresh)).isPresent();
        assertThat(embeddingStore.find(EmbeddingTarget.CANDIDATE, alreadyEmbedded))
                .hasValueSatisfying(vector -> assertThat(vector).containsExactly(9f, 9f));
    }

    @Test
    void embedsManyCandidatesInOneModelCall() throws Exception {
        answerWithOneVectorPerText();
        Long first = candidate("a@example.com", "Java developer");
        Long second = candidate("b@example.com", "Python developer");
        Long third = candidate("c@example.com", "Data engineer");

        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded", is(3)))
                .andExpect(jsonPath("$.candidateIds", hasSize(3)));

        verify(embeddingModel, times(1)).embed(anyList());
        assertThat(embeddingStore.ids(EmbeddingTarget.CANDIDATE)).containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void secondRunHasNothingLeftToEmbed() throws Exception {
        answerWithOneVectorPerText();
        candidate("a@example.com", "Java developer");

        mockMvc.perform(post("/api/v1/candidates/embeddings")).andExpect(jsonPath("$.embedded", is(1)));
        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded", is(0)))
                .andExpect(jsonPath("$.candidateIds", hasSize(0)));

        verify(embeddingModel, times(1)).embed(anyList());
    }

    @Test
    void doesNotCallTheModelWhenNoCandidateNeedsAnEmbedding() throws Exception {
        candidate("empty@example.com", null);

        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded", is(0)))
                .andExpect(jsonPath("$.skipped", is(1)));

        verify(embeddingModel, never()).embed(anyList());
    }

    @Test
    void storesNothingWhenTheModelFails() throws Exception {
        given(embeddingModel.embed(anyList())).willThrow(new IllegalStateException("model down"));
        Long id = candidate("a@example.com", "Java developer");

        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", is("Embeddings are unavailable right now")));

        assertThat(embeddingStore.find(EmbeddingTarget.CANDIDATE, id)).isEmpty();
    }

    @Test
    void rejectsAResponseWithTheWrongNumberOfVectors() throws Exception {
        given(embeddingModel.embed(anyList())).willReturn(List.of(new float[] {1f}));
        candidate("a@example.com", "Java developer");
        candidate("b@example.com", "Python developer");

        mockMvc.perform(post("/api/v1/candidates/embeddings"))
                .andExpect(status().isServiceUnavailable());

        assertThat(embeddingStore.ids(EmbeddingTarget.CANDIDATE)).isEmpty();
    }

    private void answerWithOneVectorPerText() {
        given(embeddingModel.embed(anyList())).willAnswer(invocation -> {
            List<?> texts = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new float[] {0.1f, i});
            }
            return vectors;
        });
    }

    private Long candidate(String email, String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail(email);
        candidate.setResumeText(resume);
        return candidateRepository.save(candidate).getId();
    }
}
