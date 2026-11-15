package com.benchmark.runner.scenario;

import com.benchmark.runner.model.ExperimentDtos.RunStatus;

import java.util.Map;

/**
 * Serbian (ijekavian) display names for target IDs and run statuses.
 */
public final class TargetLabels {

    private static final Map<String, String> TARGET_LABELS = Map.ofEntries(
            Map.entry("nosql-blocking", "MongoDB (blokirajući pristup)"),
            Map.entry("nosql-reactive", "MongoDB (reaktivni pristup)"),
            Map.entry("relational", "PostgreSQL (relaciona baza)"),
            Map.entry("relational-normalized", "PostgreSQL (podaci u tabelama)"),
            Map.entry("relational-cache", "PostgreSQL (keš uključen)"),
            Map.entry("relational-no-cache", "PostgreSQL (bez keša)"),
            Map.entry("mongo-cache", "MongoDB (keš uključen)"),
            Map.entry("mongo-no-cache", "MongoDB (bez keša)"),
            Map.entry("mongo-w1", "MongoDB (writeConcern: w1)"),
            Map.entry("mongo-majority", "MongoDB (writeConcern: majority)"),
            Map.entry("postgres", "PostgreSQL"));

    private TargetLabels() {
    }

    public static String target(String requestedName) {
        if (requestedName == null) {
            return "-";
        }
        return TARGET_LABELS.getOrDefault(requestedName, requestedName);
    }

    public static String statusStep(RunStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case PENDING -> "na čekanju";
            case RUNNING -> "mjerenje u toku";
            case COMPLETED -> "završeno";
            case FAILED -> "neuspješno";
            case REJECTED -> "odbijeno";
        };
    }
}
