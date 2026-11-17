package com.talentlens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import com.talentlens.ai.AiClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AiClientConfigTests {

    private final AiClientConfig config = new AiClientConfig();

    private static AiClientProperties properties(int maxAttempts, Duration readTimeout) {
        return new AiClientProperties(maxAttempts, Duration.ofMillis(1), 2.0, Duration.ofMillis(5),
                Duration.ofSeconds(2), readTimeout);
    }

    @Test
    void retriesTransientFailuresUpToMaxAttempts() {
        RetryTemplate retry = config.aiRetryTemplate(properties(3, Duration.ofSeconds(5)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(context -> {
            calls.incrementAndGet();
            throw new TransientAiException("rate limited");
        })).isInstanceOf(TransientAiException.class);

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void returnsTheResultOnceARetrySucceeds() {
        RetryTemplate retry = config.aiRetryTemplate(properties(3, Duration.ofSeconds(5)));
        AtomicInteger calls = new AtomicInteger();

        String result = retry.execute(context -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("timed out");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        RetryTemplate retry = config.aiRetryTemplate(properties(3, Duration.ofSeconds(5)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(context -> {
            calls.incrementAndGet();
            throw new NonTransientAiException("invalid api key");
        })).isInstanceOf(NonTransientAiException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void oneAttemptTurnsRetriesOff() {
        RetryTemplate retry = config.aiRetryTemplate(properties(1, Duration.ofSeconds(5)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(context -> {
            calls.incrementAndGet();
            throw new TransientAiException("server error");
        })).isInstanceOf(TransientAiException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void restClientsGiveUpAfterTheReadTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "late".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RestClient.Builder builder = RestClient.builder();
            config.aiTimeoutCustomizer(properties(1, Duration.ofMillis(200))).customize(builder);
            RestClient client = builder.baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();

            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsSettingsThatCannotWork() {
        assertThatThrownBy(() -> properties(0, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
