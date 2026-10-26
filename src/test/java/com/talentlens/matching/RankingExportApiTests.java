package com.talentlens.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RankingExportApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void exportsTheRankingAsCsvBestMatchFirst() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        Long far = candidate("Far Away", "far@example.com", "QA Engineer", new float[] {0f, 1f});
        Long exact = candidate("Doe, Jane", "jane@example.com", "Senior \"Java\" Engineer", new float[] {1f, 0f});

        String expected = "rank,candidateId,fullName,headline,score\r\n"
                + "1," + exact + ",\"Doe, Jane\",\"Senior \"\"Java\"\" Engineer\",1.0\r\n"
                + "2," + far + ",Far Away,QA Engineer,0.0\r\n";

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"job-" + jobId + "-ranked-candidates.csv\"")))
                .andExpect(content().string(expected));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void honoursTheLimit() throws Exception {
        Long jobId = job(new float[] {1f, 0f});
        Long first = candidate("First", "first@example.com", "Engineer", new float[] {1f, 0f});
        candidate("Second", "second@example.com", "Engineer", new float[] {1f, 1f});

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/export").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("rank,candidateId,fullName,headline,score\r\n"
                        + "1," + first + ",First,Engineer,1.0\r\n"));
    }

    @Test
    void returnsTheSameErrorsAsTheRanking() throws Exception {
        Long jobId = job(null);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/export")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/jobs/9999/matches/export")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches/export").param("limit", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void neutralisesSpreadsheetFormulasAndBlankValues() {
        assertThat(RankingCsvWriter.text("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(RankingCsvWriter.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(RankingCsvWriter.text("Line one\nLine two")).isEqualTo("\"Line one\nLine two\"");
        assertThat(RankingCsvWriter.text(null)).isEmpty();
        assertThat(RankingCsvWriter.write(List.of(new CandidateMatchResponse(1, 7L, "Ann", null, 0.0001))))
                .endsWith("1,7,Ann,,0.00010\r\n");
    }

    private Long job(float[] vector) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        Long id = jobRepository.save(job).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.JOB, id, vector, Instant.now());
        }
        return id;
    }

    private Long candidate(String name, String email, String headline, float[] vector) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText("Java and Spring Boot");
        candidate.setProfile(headline, 5.0, List.of("Java"), Instant.now());
        Long id = candidateRepository.save(candidate).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.CANDIDATE, id, vector, Instant.now());
        }
        return id;
    }
}
