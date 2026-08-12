package com.skillhub;

import com.skillhub.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class SkillHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
