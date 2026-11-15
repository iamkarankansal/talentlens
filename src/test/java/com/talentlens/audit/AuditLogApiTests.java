package com.talentlens.audit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentlens.application.ApplicationRepository;
import com.talentlens.application.ApplicationStatusChangeRepository;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.job.Job;
import com.talentlens.job.JobRepository;
import com.talentlens.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "talentlens.security.admin-email=boss@example.com")
@AutoConfigureMockMvc
class AuditLogApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ApplicationStatusChangeRepository statusChangeRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobRepository jobRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        auditRepository.deleteAll();
        statusChangeRepository.deleteAll();
        applicationRepository.deleteAll();
        candidateRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
        register("boss@example.com");
        adminToken = bearer("boss@example.com");
    }

    @Test
    void recordsJobChangesWithTheActingUser() throws Exception {
        String body = mockMvc.perform(post("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java and Spring Boot","status":"OPEN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/v1/jobs/" + jobId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/jobs/" + jobId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/restore").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("entityType", "job")
                        .param("entityId", String.valueOf(jobId))
                        .param("sort", "id,asc")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(4)))
                .andExpect(jsonPath("$.content[*].action",
                        contains("JOB_CREATED", "JOB_STATUS_CHANGED", "JOB_DELETED", "JOB_RESTORED")))
                .andExpect(jsonPath("$.content[*].actorEmail", everyItem(is("boss@example.com"))))
                .andExpect(jsonPath("$.content[0].details", is("Backend Engineer (OPEN)")))
                .andExpect(jsonPath("$.content[1].details", is("OPEN -> CLOSED")))
                .andExpect(jsonPath("$.content[1].entityType", is("JOB")))
                .andExpect(jsonPath("$.content[1].occurredAt", notNullValue()));
    }

    @Test
    void recordsApplicationStageChangesButNotRejectedOnes() throws Exception {
        long applicationId = apply();

        mockMvc.perform(put("/api/v1/applications/" + applicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SCREENING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/applications/" + applicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIRED\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("entityType", "APPLICATION")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action", is("APPLICATION_STATUS_CHANGED")))
                .andExpect(jsonPath("$.content[0].entityId", is((int) applicationId)))
                .andExpect(jsonPath("$.content[0].details", is("APPLIED -> SCREENING")))
                .andExpect(jsonPath("$.content[0].actorId", nullValue()))
                .andExpect(jsonPath("$.content[0].actorEmail", nullValue()));
    }

    @Test
    void onlyAdminsCanReadTheLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());

        register("riya@example.com");
        mockMvc.perform(get("/api/v1/audit-events").header(HttpHeaders.AUTHORIZATION, bearer("riya@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void entityIdNeedsAnEntityType() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")
                        .param("entityId", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
    }

    private long apply() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha.verma@example.com");
        candidate = candidateRepository.save(candidate);

        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Java and Spring Boot");
        job = jobRepository.save(job);

        String body = mockMvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":" + candidate.getId() + ",\"jobId\":" + job.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Test User","password":"a-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String bearer(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }
}
