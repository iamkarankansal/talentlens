package com.talentlens.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the container setup against regressions: the runtime image must not run as root,
 * must size the JVM from the container limits, and Compose must health-check the application.
 */
class ContainerSetupTests {

    private static List<String> runtimeStage() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("Dockerfile"));
        int lastFrom = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("FROM ")) {
                lastFrom = i;
            }
        }
        return lines.subList(lastFrom, lines.size());
    }

    @Test
    void runtimeImageRunsAsANonRootUserBeforeStartingTheApplication() throws IOException {
        List<String> stage = runtimeStage();

        int user = -1;
        int entrypoint = -1;
        for (int i = 0; i < stage.size(); i++) {
            String line = stage.get(i).trim();
            if (line.startsWith("USER ")) {
                user = i;
                assertThat(line.substring("USER ".length()).trim()).isNotIn("root", "0", "0:0");
            }
            if (line.startsWith("ENTRYPOINT ")) {
                entrypoint = i;
            }
        }

        assertThat(user).as("USER instruction in the runtime stage").isGreaterThanOrEqualTo(0);
        assertThat(entrypoint).isGreaterThan(user);
    }

    @Test
    void jvmIsSizedFromTheContainerMemoryLimit() throws IOException {
        String stage = String.join("\n", runtimeStage());

        assertThat(stage).contains("JAVA_TOOL_OPTIONS")
                .contains("-XX:MaxRAMPercentage=")
                .contains("-XX:+ExitOnOutOfMemoryError");
    }

    @Test
    void composeHealthChecksTheApplicationThroughItsReadinessProbe() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));
        String app = compose.substring(compose.indexOf("\n  app:"));

        assertThat(app).contains("healthcheck:").contains("/actuator/health/readiness");
    }

    @Test
    void buildContextLeavesOutLocalSecretsAndBuildOutput() throws IOException {
        List<String> ignored = Files.readAllLines(Path.of(".dockerignore")).stream().map(String::trim).toList();

        assertThat(ignored).contains(".env", "target", ".git");
    }
}
