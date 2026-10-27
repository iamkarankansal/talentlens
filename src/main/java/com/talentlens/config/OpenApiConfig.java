package com.talentlens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Used when the build did not write META-INF/build-info.properties, e.g. when run from an IDE. */
    static final String UNKNOWN_VERSION = "dev";

    @Bean
    public OpenAPI talentLensOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null && build.getVersion() != null ? build.getVersion() : UNKNOWN_VERSION;
        return new OpenAPI().info(new Info()
                .title("TalentLens API")
                .version(version)
                .description("AI-powered recruitment platform: jobs, candidates and model-backed resume analysis."));
    }
}
