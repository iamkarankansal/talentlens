package com.talentlens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BuildInfoApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildProperties buildProperties;

    @Test
    void buildInfoCarriesTheReleaseVersion() {
        assertThat(buildProperties.getVersion()).isEqualTo("1.0.0");
        assertThat(buildProperties.getArtifact()).isEqualTo("talentlens");
    }

    @Test
    void infoEndpointReportsTheBuildVersion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact", is("talentlens")))
                .andExpect(jsonPath("$.build.version", is("1.0.0")));
    }

    @Test
    void apiDocumentUsesTheBuildVersion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version", is(buildProperties.getVersion())));
    }
}
