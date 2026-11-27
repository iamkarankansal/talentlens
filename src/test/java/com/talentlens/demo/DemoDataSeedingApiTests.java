package com.talentlens.demo;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Starts the application with seeding on, against its own empty in-memory database. */
@SpringBootTest(properties = "talentlens.demo.seed=true")
@AutoConfigureMockMvc
class DemoDataSeedingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startupLoadsTheDemoJobsAndCandidates() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(DemoDataLoader.demoJobs().size())));
        mockMvc.perform(get("/api/v1/candidates").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(DemoDataLoader.demoCandidates().size())));
    }
}
