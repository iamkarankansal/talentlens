package com.talentlens.ratelimit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Invalid bodies are enough here: the limit is applied before the request reaches the controller, so no model is
 * ever called.
 */
@SpringBootTest(properties = {
        "talentlens.ai.rate-limit.requests=2",
        "talentlens.ai.rate-limit.window=10m"
})
@AutoConfigureMockMvc
class AiRateLimitApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void answersTooManyRequestsWithRetryAfterOnceAClientUsesUpItsAllowance() throws Exception {
        mockMvc.perform(chat("10.1.0.1")).andExpect(status().isBadRequest());
        mockMvc.perform(chat("10.1.0.1")).andExpect(status().isBadRequest());

        mockMvc.perform(chat("10.1.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, notNullValue()))
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", is("Too many AI requests, try again later")));

        // Another client still has its own allowance.
        mockMvc.perform(chat("10.1.0.2")).andExpect(status().isBadRequest());
    }

    @Test
    void doesNotLimitEndpointsThatDoNotCallTheModel() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/jobs").with(request -> {
                request.setRemoteAddr("10.2.0.1");
                return request;
            })).andExpect(status().isOk());
        }
    }

    private static RequestBuilder chat(String remoteAddress) {
        return post("/api/v1/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                });
    }
}
