package com.benchmark.nosqlreactive.config;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;

import java.util.concurrent.TimeUnit;

/**
 * Reactive Mongo client tuning, mirroring the blocking {@code MongoConfig}.
 *
 * <p>Failover behaviour: retryable reads/writes are enabled and the server selection
 * timeout is bounded, so a primary election either resolves within the timeout or the
 * request fails fast instead of hanging.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer reactiveMongoClientSettingsCustomizer(
            @Value("${app.mongo.server-selection-timeout-ms:5000}") long serverSelectionTimeoutMs,
            @Value("${app.mongo.read-preference:primary}") String readPreference,
            @Value("${app.mongo.write-concern:majority}") String writeConcern) {
        return builder -> builder
                .applyToClusterSettings(b -> b.serverSelectionTimeout(serverSelectionTimeoutMs, TimeUnit.MILLISECONDS))
                .retryReads(true)
                .retryWrites(true)
                .readPreference(resolveReadPreference(readPreference))
                // Applied at client level as well, so every operation (including transactions and
                // the ones issued by Spring Data repositories) honours the configured write concern.
                .writeConcern(resolveWriteConcern(writeConcern));
    }

    /**
     * Reactive equivalent of the blocking {@code MongoTransactionManager}; consumed through a
     * {@code TransactionalOperator} by the order flow when {@code app.orders.transactions} is on.
     */
    @Bean
    public ReactiveMongoTransactionManager reactiveMongoTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    private static ReadPreference resolveReadPreference(String value) {
        return "secondaryPreferred".equalsIgnoreCase(value)
                ? ReadPreference.secondaryPreferred()
                : ReadPreference.primary();
    }

    private static WriteConcern resolveWriteConcern(String value) {
        if (value == null) {
            return WriteConcern.MAJORITY;
        }
        return switch (value.toLowerCase()) {
            case "w1", "1" -> WriteConcern.W1;
            default -> WriteConcern.MAJORITY;
        };
    }
}
