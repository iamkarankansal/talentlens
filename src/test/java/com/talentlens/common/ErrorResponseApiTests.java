package com.talentlens.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedJsonBodyReturnsApiError() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backend Engineer\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("not valid JSON")));
    }

    @Test
    void unknownEnumValueInBodyReturnsApiError() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","description":"Java","status":"NOT_A_STATUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void missingResumeFilePartReturnsApiError() throws Exception {
        mockMvc.perform(multipart("/api/v1/candidates/1/resume"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Missing multipart field file")));
    }

    @Test
    void unsupportedMethodReturnsApiErrorWithAllowHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.message", is("Method DELETE is not supported here")));
    }
}
