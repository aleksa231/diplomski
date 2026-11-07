package com.benchmark.nosqlreactive.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Reactive HTTP client used for the stub-service call in S5. Timeouts are mandatory so
 * that a slow downstream service cannot pin an event-loop / worker thread indefinitely.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public WebClient stubWebClient(@Value("${app.stub-service.url:http://localhost:8099}") String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(15));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
