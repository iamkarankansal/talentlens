package com.talentlens.logging;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Switches console logging to structured JSON when {@code LOG_FORMAT} asks for it. {@code json} and {@code ecs} select
 * Elastic Common Schema, {@code logstash} the Logstash layout; {@code text} or no value keeps the plain pattern. Runs
 * before the logging system starts, so the very first log line already uses the chosen format. An explicit
 * {@code logging.structured.format.console} setting always wins.
 */
public class LogFormatEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String LOG_FORMAT = "LOG_FORMAT";
    static final String STRUCTURED_CONSOLE = "logging.structured.format.console";
    static final String PROPERTY_SOURCE = "talentlensLogFormat";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(STRUCTURED_CONSOLE)) {
            return;
        }
        String structuredFormat = structuredFormat(environment.getProperty(LOG_FORMAT));
        if (structuredFormat != null) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource(PROPERTY_SOURCE, Map.of(STRUCTURED_CONSOLE, structuredFormat)));
        }
    }

    /** The Spring Boot structured format for a {@code LOG_FORMAT} value, or {@code null} for plain text. */
    static String structuredFormat(String logFormat) {
        if (logFormat == null) {
            return null;
        }
        String value = logFormat.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "text", "plain" -> null;
            case "json", "ecs" -> "ecs";
            case "logstash" -> "logstash";
            default -> throw new IllegalStateException(
                    "Unsupported LOG_FORMAT '" + logFormat + "': use text, json, ecs or logstash");
        };
    }
}
