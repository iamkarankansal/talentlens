package com.talentlens.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "talentlens.security.login-throttle.max-attempts=3",
        "talentlens.security.login-throttle.lockout=10m"
})
@AutoConfigureMockMvc
class LoginThrottleApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        for (String email : new String[] {"meera@example.com", "kabir@example.com", "dev@example.com"}) {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","fullName":"Test User","password":"a-long-passphrase"}
                                    """.formatted(email)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void locksAnEmailOutAfterRepeatedFailuresEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("meera@example.com", "wrong-passphrase"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("Meera@Example.com", "a-long-passphrase"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, notNullValue()))
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", is("Too many failed logins for this email, try again later")));

        // Other accounts are not affected.
        mockMvc.perform(login("kabir@example.com", "a-long-passphrase"))
                .andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginClearsEarlierFailures() throws Exception {
        mockMvc.perform(login("dev@example.com", "wrong-passphrase")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("dev@example.com", "wrong-passphrase")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("dev@example.com", "a-long-passphrase")).andExpect(status().isOk());

        mockMvc.perform(login("dev@example.com", "wrong-passphrase")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("dev@example.com", "wrong-passphrase")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("dev@example.com", "a-long-passphrase")).andExpect(status().isOk());
    }

    @Test
    void throttlesUnknownEmailsToo() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("ghost@example.com", "any-passphrase"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("ghost@example.com", "any-passphrase"))
                .andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }
}
