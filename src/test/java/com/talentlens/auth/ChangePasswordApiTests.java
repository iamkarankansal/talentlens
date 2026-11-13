package com.talentlens.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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

@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"riya@example.com","fullName":"Riya Sen","password":"a-long-passphrase"}
                                """))
                .andExpect(status().isCreated());
        token = login("a-long-passphrase");
    }

    @Test
    void changesThePasswordSoOnlyTheNewOneLogsIn() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("a-long-passphrase", "a-new-passphrase")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("a-long-passphrase")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("a-new-passphrase")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAWrongCurrentPassword() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("not-my-password", "a-new-passphrase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The current password is incorrect")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("a-long-passphrase")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsReusingTheCurrentPassword() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("a-long-passphrase", "a-long-passphrase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The new password must differ from the current one")));
    }

    @Test
    void validatesTheNewPasswordLength() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("a-long-passphrase", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword", notNullValue()));
    }

    @Test
    void requiresABearerToken() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("a-long-passphrase", "a-new-passphrase")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(change("a-long-passphrase", "a-new-passphrase")))
                .andExpect(status().isUnauthorized());
    }

    private String login(String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String credentials(String password) {
        return """
                {"email":"riya@example.com","password":"%s"}
                """.formatted(password);
    }

    private static String change(String currentPassword, String newPassword) {
        return """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(currentPassword, newPassword);
    }
}
