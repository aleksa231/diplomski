package com.benchmark.runner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP clients with explicit timeouts (spec section 7: timeouts on every client). The runner never
 * blocks forever on Prometheus or on a health probe of a service that just went down.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient prometheusRestClient(RunnerProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getPrometheus().getUrl())
                .requestFactory(requestFactory(2, 10))
                .build();
    }

    /** Health probes and cache toggles — short timeouts so a dead service fails fast. */
    @Bean
    public RestClient serviceRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(1, 3))
                .build();
    }

    /**
     * Admin setup (S4 index create/drop on the seeded catalog can exceed a few seconds). A short
     * read timeout here produced false "indexes FAILED" entries and invalid S4 comparisons.
     */
    @Bean
    public RestClient adminSetupRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(2, 120))
                .build();
    }

    private ClientHttpRequestFactory requestFactory(int connectTimeoutSec, int readTimeoutSec) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSec));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSec));
        return factory;
    }
}
