package com.benchmark.runner.scenario;

import com.benchmark.runner.model.ExperimentDtos.RunStatus;

import java.util.Map;

/**
 * Serbian (ijekavian) display names for target IDs and run statuses, used in the run progress and
 * the run log so everything the console shows while a measurement is running is in Serbian.
 * Mirrors {@code frontend/src/labels.ts} so the progress line and the chips/tiles name the same
 * system the same way.
 */
public final class TargetLabels {

    private static final Map<String, String> TARGET_LABELS = Map.ofEntries(
            Map.entry("nosql-blocking", "MongoDB (blokirajući pristup)"),
            Map.entry("nosql-reactive", "MongoDB (reaktivni pristup)"),
            Map.entry("nosql-reactive-cache", "MongoDB, reaktivni (keš uključen)"),
            Map.entry("nosql-reactive-no-cache", "MongoDB, reaktivni (bez keša)"),
            Map.entry("relational", "PostgreSQL (relaciona baza)"),
            Map.entry("relational-normalized", "PostgreSQL (podaci u tabelama)"),
            Map.entry("relational-jsonb", "PostgreSQL (podaci u JSON obliku)"),
            Map.entry("relational-jsonb-indexed", "PostgreSQL, JSON (sa indeksom)"),
            Map.entry("relational-jsonb-unindexed", "PostgreSQL, JSON (bez indeksa)"),
            Map.entry("relational-cache", "PostgreSQL (keš uključen)"),
            Map.entry("relational-no-cache", "PostgreSQL (bez keša)"),
            Map.entry("mongo-cache", "MongoDB (keš uključen)"),
            Map.entry("mongo-no-cache", "MongoDB (bez keša)"),
            Map.entry("mongo-indexed", "MongoDB (sa indeksom)"),
            Map.entry("mongo-unindexed", "MongoDB (bez indeksa)"),
            Map.entry("mongo-w1", "MongoDB (writeConcern: w1)"),
            Map.entry("mongo-majority", "MongoDB (writeConcern: majority)"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("postgres-indexed", "PostgreSQL (sa indeksom)"),
            Map.entry("postgres-unindexed", "PostgreSQL (bez indeksa)"),
            Map.entry("failover", "Prebacivanje na rezervnu bazu (kvar)"),
            Map.entry("redis-down", "Rad bez keša (Redis isključen)"),
            Map.entry("concurrency", "Istovremeni pristup (više korisnika odjednom)"));

    private TargetLabels() {
    }

    /** Serbian display name for a requested target ID; the ID itself when unknown. */
    public static String target(String requestedName) {
        if (requestedName == null) {
            return "—";
        }
        return TARGET_LABELS.getOrDefault(requestedName, requestedName);
    }

    /** Serbian progress-step text for a run status. */
    public static String statusStep(RunStatus status) {
        if (status == null) {
            return "—";
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
