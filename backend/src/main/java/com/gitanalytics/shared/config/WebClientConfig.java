package com.gitanalytics.shared.config;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClientCustomizer webClientCustomizer() {
        return builder -> builder.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }
}
