package com.talentlens.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.talentlens.ai.AiClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;

@SpringBootTest(properties = {
        "talentlens.ai.client.max-attempts=5",
        "talentlens.ai.client.read-timeout=90s"
})
class AiClientWiringTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AiClientProperties properties;

    @Test
    void bindsTheClientSettings() {
        assertThat(properties.maxAttempts()).isEqualTo(5);
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void modelsReceiveTheConfiguredRetryTemplate() {
        assertThat(context.getBean(RetryTemplate.class)).isSameAs(context.getBean("aiRetryTemplate"));
    }
}
