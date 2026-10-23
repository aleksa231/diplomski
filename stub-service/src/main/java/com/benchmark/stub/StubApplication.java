package com.benchmark.stub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class StubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StubApplication.class, args);
    }

    @GetMapping("/stub/recommendations")
    public List<Map<String, Object>> getRecommendations(
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(required = false) String productId) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        return List.of(
                Map.of("id", "rec-1", "name", "Recommended Product 1", "score", 0.95),
                Map.of("id", "rec-2", "name", "Recommended Product 2", "score", 0.88),
                Map.of("id", "rec-3", "name", "Recommended Product 3", "score", 0.76)
        );
    }
}
