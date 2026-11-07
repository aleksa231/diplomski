package com.benchmark.runner.service;

import com.benchmark.runner.model.ExperimentDtos.ExplainSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures the query plan of the five S4 benchmark queries from the target that is about to be
 * measured.
 * <p>
 * The target name ({@code mongo-indexed}, {@code postgres-unindexed}, ...) expresses intent; only the
 * plan proves the configuration was actually applied. Recording the plan next to the measurement is
 * what lets the thesis say "the report query used an index scan" instead of "the target was called
 * indexed". A plan that cannot be captured (for example the MongoDB {@code $text} query without a
 * text index) is stored with its error instead of being silently dropped.
 */
@Component
public class ExplainProbeService {

    private static final Logger log = LoggerFactory.getLogger(ExplainProbeService.class);

    /** S4 (join-heavy): catalog + windowed reports + full-dataset contrasts. */
    static final List<String> S4_QUERIES = List.of("Q1", "Q2", "Q3", "Q4", "Q5");
    /** S4B (isolated): catalog + windowed order-totals (no product join) + full contrast. */
    static final List<String> S4B_QUERIES = List.of("Q1", "Q6", "Q7");

    /** MongoDB reports plan nodes as {@code "stage": "IXSCAN"}. */
    private static final Pattern MONGO_STAGE = Pattern.compile("\"stage\"\\s*:\\s*\"([A-Z_]+)\"");

    /** Plan nodes that mean an index produced the candidate documents. */
    private static final Set<String> MONGO_INDEX_STAGES =
            Set.of("IXSCAN", "TEXT_MATCH", "DISTINCT_SCAN", "IDHACK", "GEO_NEAR_2DSPHERE");

    private final RestClient adminSetupRestClient;

    public ExplainProbeService(@Qualifier("adminSetupRestClient") RestClient adminSetupRestClient) {
        this.adminSetupRestClient = adminSetupRestClient;
    }

    public List<ExplainSnapshot> capture(String baseUrl, Consumer<String> logSink) {
        return capture(baseUrl, "S4", logSink);
    }

    /**
     * Reads the scenario's explain plans from one target. Never throws: a broken explain endpoint
     * must not abort a benchmark run, it is recorded as an error on the snapshot.
     */
    public List<ExplainSnapshot> capture(String baseUrl, String scenario, Consumer<String> logSink) {
        List<String> queries = queriesFor(scenario);
        List<ExplainSnapshot> snapshots = new ArrayList<>(queries.size());
        for (String query : queries) {
            snapshots.add(captureOne(baseUrl, query, logSink));
        }
        String summary = snapshots.stream()
                .map(s -> s.getQuery() + "=" + (s.getError() != null ? "n/a" : s.getScanType()))
                .reduce((a, b) -> a + " " + b)
                .orElse("n/a");
        logSink.accept("[runner] explain plans: " + summary);
        return snapshots;
    }

    private List<String> queriesFor(String scenario) {
        String code = scenario == null ? "" : scenario.trim().toUpperCase(Locale.ROOT);
        return "S4B".equals(code) ? S4B_QUERIES : S4_QUERIES;
    }

    private ExplainSnapshot captureOne(String baseUrl, String query, Consumer<String> logSink) {
        try {
            JsonNode body = adminSetupRestClient.get()
                    .uri(baseUrl + "/api/admin/explain?query=" + query)
                    .retrieve()
                    .body(JsonNode.class);
            String plan = body == null ? "" : body.path("plan").asText("");
            if (plan.isBlank()) {
                return ExplainSnapshot.builder()
                        .query(query)
                        .scanType("UNKNOWN")
                        .error("empty plan returned")
                        .build();
            }
            List<String> stages = stages(plan);
            String scanType = scanType(plan, stages);
            return ExplainSnapshot.builder()
                    .query(query)
                    .scanType(scanType)
                    .usedIndex(!"COLLECTION_SCAN".equals(scanType)
                            && !"SEQUENTIAL_SCAN".equals(scanType)
                            && !"UNKNOWN".equals(scanType))
                    .stages(stages)
                    .plan(plan)
                    .build();
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[runner] explain {} on {} failed: {}", query, baseUrl, message);
            logSink.accept("[runner] explain " + query + " unavailable: " + message);
            return ExplainSnapshot.builder()
                    .query(query)
                    .scanType("UNKNOWN")
                    .error(message)
                    .build();
        }
    }

    /**
     * Extracts the plan nodes from either plan dialect: MongoDB returns JSON with {@code stage}
     * fields, PostgreSQL returns {@code EXPLAIN} text. The result is de-duplicated and keeps the
     * order in which the planner emitted the nodes.
     */
    static List<String> stages(String plan) {
        Set<String> stages = new LinkedHashSet<>();
        Matcher mongo = MONGO_STAGE.matcher(plan);
        while (mongo.find()) {
            stages.add(mongo.group(1));
        }
        for (String node : List.of("Index Only Scan", "Bitmap Index Scan", "Index Scan Backward",
                "Index Scan", "Bitmap Heap Scan", "Seq Scan", "Hash Join", "Nested Loop",
                "Merge Join", "Aggregate", "Sort", "Gather")) {
            if (plan.contains(node)) {
                stages.add(node.toUpperCase(Locale.ROOT).replace(' ', '_'));
            }
        }
        return new ArrayList<>(stages);
    }

    /**
     * Normalises the access path. A plan that uses an index anywhere is reported as {@code INDEX}
     * even when it also contains a sequential scan (for example the small {@code category} table is
     * read sequentially next to an indexed {@code product} scan).
     */
    static String scanType(String plan, List<String> stages) {
        boolean mongoIndex = stages.stream().anyMatch(MONGO_INDEX_STAGES::contains);
        boolean pgIndex = plan.contains("Index Scan")
                || plan.contains("Index Only Scan")
                || plan.contains("Bitmap Index Scan")
                || stages.contains("INDEX_SCAN")
                || stages.contains("INDEX_ONLY_SCAN")
                || stages.contains("BITMAP_INDEX_SCAN");
        if (mongoIndex || pgIndex) {
            return "INDEX";
        }
        if (stages.contains("COLLSCAN") || stages.contains("SEQ_SCAN")) {
            return stages.contains("COLLSCAN") ? "COLLECTION_SCAN" : "SEQUENTIAL_SCAN";
        }
        return "UNKNOWN";
    }
}
