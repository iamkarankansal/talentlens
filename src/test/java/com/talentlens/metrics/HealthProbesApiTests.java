package com.talentlens.metrics;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthProbesApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void livenessReportsOnlyTheApplicationState() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.livenessState.status", is("UP")))
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    void readinessIncludesTheDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.readinessState.status", is("UP")))
                .andExpect(jsonPath("$.components.db.status", is("UP")));
    }

    @Test
    void readinessAnswers503WhileRefusingTraffic() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status", is("OUT_OF_SERVICE")));
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }
}
