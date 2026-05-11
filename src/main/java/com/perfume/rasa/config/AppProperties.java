package com.perfume.rasa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Cors cors = new Cors();

    @Data
    public static class Cors {
        private List<String> allowedOriginPatterns;
        private Boolean allowCredentials;
    }
}
