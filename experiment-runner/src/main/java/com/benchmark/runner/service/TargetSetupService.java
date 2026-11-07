package com.benchmark.runner.service;

import com.benchmark.runner.scenario.TargetCatalog.ResolvedTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies the runtime prerequisites a target name implies (cache on/off, experiment indexes on/off)
 * through the admin API every service exposes (spec section 6). A failure is reported in the run log
 * and recorded in the target's setup list, because a measurement taken with the wrong configuration
 * must not silently pass as valid.
 */
@Component
public class TargetSetupService {

    private static final Logger log = LoggerFactory.getLogger(TargetSetupService.class);

    private final RestClient adminSetupRestClient;

    public TargetSetupService(RestClient adminSetupRestClient) {
        this.adminSetupRestClient = adminSetupRestClient;
    }

    public void apply(ResolvedTarget target, Consumer<String> logSink) {
        String baseUrl = target.definition().getBaseUrl();
        if (target.cacheEnabled() != null) {
            post(baseUrl + "/api/admin/cache", Map.of("enabled", target.cacheEnabled()),
                    "cache=" + target.cacheEnabled(), target, logSink);
        }
        if (target.indexesEnabled() != null) {
            post(baseUrl + "/api/admin/indexes", Map.of("enabled", target.indexesEnabled()),
                    "indexes=" + target.indexesEnabled(), target, logSink);
        }
    }

    public void applyS5(ResolvedTarget target, Consumer<String> logSink) {
        post(target.definition().getBaseUrl() + "/api/admin/threads", Map.of("max", 50),
                "threads=50", target, logSink);
    }

    public void resetS5(ResolvedTarget target, Consumer<String> logSink) {
        post(target.definition().getBaseUrl() + "/api/admin/threads", Map.of("max", 200),
                "threads=200", target, logSink);
    }

    private void post(String url, Map<String, Object> body, String label, ResolvedTarget target,
                      Consumer<String> logSink) {
        try {
            adminSetupRestClient.post().uri(url).body(body).retrieve().toBodilessEntity();
            logSink.accept("[runner] " + target.requestedName() + ": applied " + label);
        } catch (Exception e) {
            String message = label + " FAILED (" + e.getMessage() + ")";
            log.warn("Target {}: {} on {}", target.requestedName(), message, url);
            logSink.accept("[runner] " + target.requestedName() + ": " + message);
            target.setup().remove(label);
            target.setup().add(message);
        }
    }
}
