package com.talentlens.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LogFormatEnvironmentPostProcessorTests {

    private final LogFormatEnvironmentPostProcessor processor = new LogFormatEnvironmentPostProcessor();

    @Test
    void keepsPlainTextLoggingWhenLogFormatIsNotSet() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    }

    @Test
    void jsonSelectsElasticCommonSchema() {
        MockEnvironment environment = new MockEnvironment().withProperty("LOG_FORMAT", " JSON ");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    void logstashSelectsTheLogstashLayout() {
        MockEnvironment environment = new MockEnvironment().withProperty("LOG_FORMAT", "logstash");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    }

    @Test
    void textKeepsPlainLogging() {
        MockEnvironment environment = new MockEnvironment().withProperty("LOG_FORMAT", "text");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    }

    @Test
    void anExplicitStructuredFormatWins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LOG_FORMAT", "json")
                .withProperty("logging.structured.format.console", "logstash");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    }

    @Test
    void rejectsAnUnknownFormat() {
        MockEnvironment environment = new MockEnvironment().withProperty("LOG_FORMAT", "xml");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOG_FORMAT");
    }
}
