package com.benchmark.nosqlblocking.config;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.util.concurrent.TimeUnit;

/**
 * Mongo client tuning shared by the blocking NoSQL service.
 *
 * <p>Failover behaviour: retryable reads/writes are enabled and the server selection
 * timeout is bounded, so a primary election either resolves within the timeout or the
 * request fails fast instead of hanging. The write concern is fixed to {@code majority}.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoClientSettingsCustomizer(
            @Value("${app.mongo.server-selection-timeout-ms:5000}") long serverSelectionTimeoutMs,
            @Value("${app.mongo.read-preference:primary}") String readPreference) {
        return builder -> builder
                .applyToClusterSettings(b -> b.serverSelectionTimeout(serverSelectionTimeoutMs, TimeUnit.MILLISECONDS))
                .retryReads(true)
                .retryWrites(true)
                .readPreference(resolveReadPreference(readPreference))
                .writeConcern(WriteConcern.MAJORITY);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory,
                                       MongoConverter converter) {
        MongoTemplate template = new MongoTemplate(factory, converter);
        template.setWriteConcern(WriteConcern.MAJORITY);
        return template;
    }

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    private static ReadPreference resolveReadPreference(String value) {
        return "secondaryPreferred".equalsIgnoreCase(value)
                ? ReadPreference.secondaryPreferred()
                : ReadPreference.primary();
    }
}
