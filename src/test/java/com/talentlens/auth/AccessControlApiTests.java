package com.talentlens.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentlens.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "talentlens.security.auth-enforced=true",
        "talentlens.security.admin-email=boss@example.com"})
@AutoConfigureMockMvc
class AccessControlApiTests {

    private static final String JOB = """
            {"title":"Backend Engineer","description":"Java and Spring Boot"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private long candidateUserId;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        register("boss@example.com");
        candidateUserId = register("riya@example.com");
    }

    @Test
    void writesNeedAToken() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(JOB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void candidatesCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("riya@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsAndRecruitersCanWrite() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/users/" + candidateUserId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RECRUITER"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("riya@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Backend Engineer")));
    }

    @Test
    void readsStayOpen() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + candidateUserId))
                .andExpect(status().isOk());
    }

    @Test
    void aiReadsNeedAStaffToken() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/1/matches"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs/1/matches/2/skill-gap")
                        .header(HttpHeaders.AUTHORIZATION, bearer("riya@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Who knows Java?"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void interceptorClassifiesPaths() {
        AccessControlInterceptor interceptor = new AccessControlInterceptor(null);
        assertThat(interceptor.requiresStaff("POST", "/api/v1/auth/login")).isFalse();
        assertThat(interceptor.requiresStaff("POST", "/api/v1/users")).isFalse();
        assertThat(interceptor.requiresStaff("GET", "/api/v1/candidates")).isFalse();
        assertThat(interceptor.requiresStaff("DELETE", "/api/v1/candidates/3")).isTrue();
        assertThat(interceptor.requiresStaff("GET", "/api/v1/jobs/3/matches/export"))
                .isTrue();
        assertThat(interceptor.requiresStaff("GET", "/actuator/health")).isFalse();
    }

    private long register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Test User","password":"a-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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
