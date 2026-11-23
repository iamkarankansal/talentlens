package com.talentlens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ModelCallMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ModelCallMetrics metrics = new ModelCallMetrics(registry);

    @Test
    void recordsSuccessfulCallsAndReturnsTheirResult() {
        String result = metrics.record(ModelCallMetrics.RESUME_PARSING, () -> "parsed");

        assertThat(result).isEqualTo("parsed");
        Timer timer = registry.find(ModelCallMetrics.TIMER)
                .tag("operation", "resume-parsing")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordsFailedCallsAndRethrowsTheException() {
        IllegalStateException failure = new IllegalStateException("model down");

        assertThatThrownBy(() -> metrics.record(ModelCallMetrics.SHORTLIST, () -> {
            throw failure;
        })).isSameAs(failure);

        Timer timer = registry.find(ModelCallMetrics.TIMER)
                .tag("operation", "shortlist")
                .tag("outcome", "failure")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(registry.find(ModelCallMetrics.TIMER).tag("outcome", "success").timer()).isNull();
    }
}
