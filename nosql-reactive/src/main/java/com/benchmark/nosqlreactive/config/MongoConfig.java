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
 * request fails fast instead of hanging. The write concern is fixed to {@code majority}.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer reactiveMongoClientSettingsCustomizer(
            @Value("${app.mongo.server-selection-timeout-ms:5000}") long serverSelectionTimeoutMs,
            @Value("${app.mongo.read-preference:primary}") String readPreference) {
        return builder -> builder
                .applyToClusterSettings(b -> b.serverSelectionTimeout(serverSelectionTimeoutMs, TimeUnit.MILLISECONDS))
                .retryReads(true)
                .retryWrites(true)
                .readPreference(resolveReadPreference(readPreference))
                .writeConcern(WriteConcern.MAJORITY);
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
}