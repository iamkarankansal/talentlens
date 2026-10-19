package com.talentlens.user;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "talentlens.security.admin-email=Boss@Example.com")
@AutoConfigureMockMvc
class RoleApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private long adminId;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        adminId = register("boss@example.com", "ADMIN");
        userId = register("riya@example.com", "CANDIDATE");
    }

    @Test
    void newUsersAreCandidatesAndTheConfiguredEmailIsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("CANDIDATE")));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void anAdminPromotesAUserAndTheChangeAppliesToExistingTokens() throws Exception {
        String userToken = bearer("riya@example.com");

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) userId)))
                .andExpect(jsonPath("$.role", is("RECRUITER")));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("RECRUITER")));
    }

    @Test
    void onlyAnAdminCanChangeRoles() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("ADMIN")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("riya@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", notNullValue()));

        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(jsonPath("$.role", is("CANDIDATE")));
    }

    @Test
    void keepsAtLeastOneAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + adminId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("RECRUITER")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/" + adminId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("RECRUITER")));
    }

    @Test
    void validatesTheRoleAndTheUser() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role", notNullValue()));

        mockMvc.perform(put("/api/v1/users/999999/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer("boss@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(role("RECRUITER")))
                .andExpect(status().isNotFound());
    }

    private long register(String email, String expectedRole) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Test User","password":"a-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is(expectedRole)))
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

    private static String role(String role) {
        return """
                {"role":"%s"}
                """.formatted(role);
    }
}
