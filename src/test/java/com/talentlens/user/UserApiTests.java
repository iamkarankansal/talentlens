package com.talentlens.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void registersAUserAndStoresOnlyAPasswordHash() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Asha@Example.com", "Asha Rao", "correct-horse")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/users/")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("asha@example.com")))
                .andExpect(jsonPath("$.fullName", is("Asha Rao")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        UserAccount stored = repository.findByEmailIgnoreCase("asha@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse").startsWith("$2");
        assertThat(passwordEncoder.matches("correct-horse", stored.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", stored.getPasswordHash())).isFalse();
    }

    @Test
    void saltsEachHashSoEqualPasswordsDiffer() throws Exception {
        register("one@example.com", "same-password");
        register("two@example.com", "same-password");

        String first = repository.findByEmailIgnoreCase("one@example.com").orElseThrow().getPasswordHash();
        String second = repository.findByEmailIgnoreCase("two@example.com").orElseThrow().getPasswordHash();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsADuplicateEmailIgnoringCase() throws Exception {
        register("dev@example.com", "first-password");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("DEV@example.com", "Dev Shah", "second-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesEmailNameAndPasswordLength() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", "", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.fullName", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("long@example.com", "Long Password", "x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    void readsAUserByIdAndReturnsNotFoundForUnknownIds() throws Exception {
        Long id = register("eva@example.com", "eva-password");

        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("eva@example.com")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/users/999999"))
                .andExpect(status().isNotFound());
    }

    private Long register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "Test User", password)))
                .andExpect(status().isCreated());
        return repository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private static String body(String email, String fullName, String password) {
        return """
                {"email":"%s","fullName":"%s","password":"%s"}
                """.formatted(email, fullName, password);
    }
}
