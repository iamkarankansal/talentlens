package com.talentlens.config;

import java.net.http.HttpClient;

import com.talentlens.ai.AiClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

/**
 * Retries and timeouts for the model provider. Spring AI's OpenAI chat and embedding models take the
 * {@link RetryTemplate} defined here in place of Spring AI's default one, and build their HTTP client from the
 * auto-configured {@code RestClient.Builder}, which picks up the timeouts through the customizer.
 */
@Configuration
@EnableConfigurationProperties(AiClientProperties.class)
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    /**
     * Retries rate limits and server errors (which Spring AI reports as {@link TransientAiException}) and
     * connection failures or timeouts. Client errors such as a bad API key are not retried.
     */
    @Bean
    @Primary
    public RetryTemplate aiRetryTemplate(AiClientProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.maxAttempts())
                .exponentialBackoff(properties.initialBackoff().toMillis(), properties.backoffMultiplier(),
                        properties.maxBackoff().toMillis())
                .retryOn(TransientAiException.class)
                .retryOn(ResourceAccessException.class)
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                            Throwable throwable) {
                        log.warn("Model call failed (attempt {} of {}): {}", context.getRetryCount(),
                                properties.maxAttempts(), throwable.getMessage());
                    }
                })
                .build();
    }

    @Bean
    public RestClientCustomizer aiTimeoutCustomizer(AiClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder -> builder.requestFactory(requestFactory);
    }
}
