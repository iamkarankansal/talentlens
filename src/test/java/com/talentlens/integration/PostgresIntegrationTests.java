package com.talentlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.talentlens.application.ApplicationRepository;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.matching.EmbeddingStore;
import com.talentlens.matching.EmbeddingTarget;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the application against a real PostgreSQL with pgvector, the same image docker-compose uses,
 * so the PostgreSQL-only migrations, vector columns and queries are exercised. Skipped when Docker
 * is not available; the rest of the suite still runs on H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EmbeddingStore embeddingStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clean() {
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void appliesEveryMigrationIncludingThePgvectorTables() {
        assertThat(jdbcTemplate.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'vector'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select udt_name from information_schema.columns"
                        + " where table_name = 'candidate_embeddings' and column_name = 'embedding'",
                String.class)).isEqualTo("vector");
    }

    @Test
    void storesVectorsInPgvectorAndRanksCandidates() throws Exception {
        Long jobId = job(new float[] {1f, 0f, 0f});
        Long far = candidate("Far Away", "far@example.com", new float[] {0f, 1f, 0f});
        Long exact = candidate("Exact Match", "exact@example.com", new float[] {2f, 0f, 0f});

        assertThat(embeddingStore.find(EmbeddingTarget.JOB, jobId)).hasValueSatisfying(
                vector -> assertThat(vector).containsExactly(1f, 0f, 0f));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatesConsidered", is(2)))
                .andExpect(jsonPath("$.matches", hasSize(2)))
                .andExpect(jsonPath("$.matches[0].candidateId", is(exact.intValue())))
                .andExpect(jsonPath("$.matches[0].score", closeTo(1.0, 0.0001)))
                .andExpect(jsonPath("$.matches[1].candidateId", is(far.intValue())));
    }

    @Test
    void deletingACandidateCascadesToItsEmbedding() {
        Long id = candidate("Asha Verma", "asha@example.com", new float[] {0.5f, 0.5f});

        candidateRepository.deleteById(id);

        assertThat(embeddingStore.find(EmbeddingTarget.CANDIDATE, id)).isEmpty();
    }

    @Test
    void searchMatchesWildcardCharactersLiterally() throws Exception {
        candidate("Underscore Email", "a_b@example.com", null);
        candidate("Plain Email", "axb@example.com", null);

        mockMvc.perform(get("/api/v1/candidates").param("q", "a_b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName", contains("Underscore Email")));
    }

    @Test
    void createsAJobAndAnApplicationThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Data Engineer","description":"Pipelines in Spark","location":"Pune"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Data Engineer")));
        Long jobId = jobRepository.findAll().get(0).getId();
        Long candidateId = candidate("Meera Nair", "meera@example.com", null);

        mockMvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":" + candidateId + ",\"jobId\":" + jobId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPLIED")));
    }

    private Long job(float[] vector) {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Build APIs in Java");
        Long id = jobRepository.save(job).getId();
        embeddingStore.save(EmbeddingTarget.JOB, id, vector, Instant.now());
        return id;
    }

    private Long candidate(String name, String email, float[] vector) {
        Candidate candidate = new Candidate();
        candidate.setFullName(name);
        candidate.setEmail(email);
        candidate.setResumeText("Java and Spring Boot");
        candidate.setProfile("Backend Engineer", 5.0, List.of("Java"), Instant.now());
        Long id = candidateRepository.save(candidate).getId();
        if (vector != null) {
            embeddingStore.save(EmbeddingTarget.CANDIDATE, id, vector, Instant.now());
        }
        return id;
    }
}
