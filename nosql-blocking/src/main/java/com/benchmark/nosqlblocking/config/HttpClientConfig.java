package com.benchmark.nosqlblocking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client used for the stub-service call in S5. Timeouts are mandatory so that a
 * slow downstream service cannot exhaust the request threads of the blocking stack.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient stubRestClient(@Value("${app.stub-service.url:http://localhost:8099}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
