package com.talentlens.config;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesOpenApiDocumentDescribingTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", is("TalentLens API")))
                .andExpect(jsonPath("$.info.version", is("v1")))
                .andExpect(jsonPath("$.paths['/api/v1/jobs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/jobs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/candidates'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/candidates/{candidateId}/resume/parse'].post").exists());
    }

    @Test
    void documentsListParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/jobs'].get.parameters[?(@.name == 'status')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/candidates'].get.parameters[?(@.name == 'q')]").exists());
    }

    @Test
    void redirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
