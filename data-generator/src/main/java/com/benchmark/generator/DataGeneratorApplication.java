package com.benchmark.generator;

import com.benchmark.generator.core.DataModelGenerator;
import com.benchmark.generator.seeder.MongoSeeder;
import com.benchmark.generator.seeder.PostgresSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
public class DataGeneratorApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorApplication.class);

    private final MongoSeeder mongoSeeder;
    private final PostgresSeeder postgresSeeder;

    public DataGeneratorApplication(MongoSeeder mongoSeeder, PostgresSeeder postgresSeeder) {
        this.mongoSeeder = mongoSeeder;
        this.postgresSeeder = postgresSeeder;
    }

    public static void main(String[] args) {
        SpringApplication.run(DataGeneratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        int size = 10000;
        long seed = 42L;
        double ordersRatio = 2.0;
        Set<String> targets = new HashSet<>(Arrays.asList("mongo", "pg-normalized", "pg-jsonb"));
        boolean reset = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--size" -> {
                    if (i + 1 < args.length) size = Integer.parseInt(args[++i]);
                }
                case "--seed" -> {
                    if (i + 1 < args.length) seed = Long.parseLong(args[++i]);
                }
                case "--orders-ratio" -> {
                    if (i + 1 < args.length) ordersRatio = Double.parseDouble(args[++i]);
                }
                case "--targets" -> {
                    if (i + 1 < args.length) {
                        targets = new HashSet<>(Arrays.asList(args[++i].split(",")));
                    }
                }
                case "--reset" -> reset = true;
            }
        }

        log.info("=================================================================");
        log.info("Starting Data Generator CLI");
        log.info("Configuration: size={}, seed={}, ordersRatio={}, targets={}, reset={}",
                size, seed, ordersRatio, targets, reset);
        log.info("=================================================================");

        long startTime = System.currentTimeMillis();

        if (reset) {
            if (targets.contains("mongo")) {
                mongoSeeder.reset();
            }
            if (targets.contains("pg-normalized") || targets.contains("pg-jsonb")) {
                postgresSeeder.reset();
            }
        }

        log.info("Generating deterministic in-memory datasets...");
        var products = DataModelGenerator.generateProducts(seed, size);
        int orderCount = (int) (size * ordersRatio);
        var orders = DataModelGenerator.generateOrders(seed, orderCount, products);
        log.info("Generated {} products and {} orders in memory (took {} ms).",
                products.size(), orders.size(), (System.currentTimeMillis() - startTime));

        if (targets.contains("mongo")) {
            long t0 = System.currentTimeMillis();
            mongoSeeder.seedProducts(products);
            mongoSeeder.seedOrders(orders);
            log.info("[Target: MongoDB] Seeding completed in {} ms.", (System.currentTimeMillis() - t0));
        }

        if (targets.contains("pg-normalized")) {
            long t0 = System.currentTimeMillis();
            postgresSeeder.seedNormalized(products, orders);
            log.info("[Target: PG-Normalized] Seeding completed in {} ms.", (System.currentTimeMillis() - t0));
        }

        if (targets.contains("pg-jsonb")) {
            long t0 = System.currentTimeMillis();
            postgresSeeder.seedJsonb(products);
            log.info("[Target: PG-JSONB] Seeding completed in {} ms.", (System.currentTimeMillis() - t0));
        }

        log.info("=================================================================");
        log.info("All target seedings finished successfully in {} ms total.",
                (System.currentTimeMillis() - startTime));
        log.info("=================================================================");
    }
}
