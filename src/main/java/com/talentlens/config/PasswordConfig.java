package com.talentlens.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt hashes passwords with a random salt; the strength (log rounds, 4 to 31) comes from configuration.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${talentlens.security.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
