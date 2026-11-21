package com.talentlens.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void echoesAWellFormedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").header("X-Request-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", is("abc-123")));
    }

    @Test
    void generatesARequestIdWhenNoneIsSent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    void replacesAMalformedRequestId() throws Exception {
        String malformed = "bad id with spaces and <script>";
        MvcResult result = mockMvc.perform(get("/api/v1/jobs").header("X-Request-Id", malformed))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotEqualTo(malformed);
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    void errorResponsesCarryTheRequestIdToo() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/999999").header("X-Request-Id", "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", is("trace-404")));
    }

    @Test
    void clearsTheLoggingContextAfterTheRequest() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").header("X-Request-Id", "short-lived"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void overlongIdsAreReplaced() {
        String overlong = "a".repeat(65);
        assertThat(RequestIdFilter.resolve(overlong)).isNotEqualTo(overlong);
        assertThat(RequestIdFilter.resolve("  padded-id  ")).isEqualTo("padded-id");
    }
}
