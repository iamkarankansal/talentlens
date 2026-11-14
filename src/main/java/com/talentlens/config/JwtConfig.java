package com.talentlens.config;

import com.talentlens.auth.JwtProperties;
import com.talentlens.auth.LoginThrottleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, LoginThrottleProperties.class})
public class JwtConfig {
}
