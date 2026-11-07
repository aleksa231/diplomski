package com.benchmark.nosqlblocking;

import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.nosqlblocking.domain.ProductDocument;
import com.benchmark.nosqlblocking.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S7 (b) - referential integrity, the NoSQL side of the comparison.
 *
 * <p>This is a correctness property of the storage engine, not a load characteristic, so nothing
 * meaningful can be measured with k6; it is asserted here instead. See docs/s7-integrity.md.
 *
 * <p>MongoDB has no separate {@code category} entity: the category is a plain denormalized string
 * on each product document. There is therefore no foreign key to declare and nothing for the
 * database to enforce - a product may reference a category that does not exist anywhere, and
 * removing the last product that used a category simply makes that category vanish with no
 * constraint firing. The relational counterpart ({@code RelationalReferentialIntegrityTest})
 * shows PostgreSQL refusing the destructive operation through
 * {@code REFERENCES category(id) ON DELETE RESTRICT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class NosqlReferentialIntegrityTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("app.cache.enabled", () -> "false");
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void categoryIsOnlyADenormalizedFieldSoThereIsNoCategoryCollectionOrForeignKey() {
        String category = "s7-integrity-" + UUID.randomUUID().toString().substring(0, 8);
        ProductDto product = createProduct(category);

        // The category exists only as a string inside the product document.
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(productCountInCategory(category)).isEqualTo(1);

        // Unlike PostgreSQL, where V1__init_schema.sql creates a dedicated category table with a
        // foreign key, MongoDB stores nothing about the category on its own.
        assertThat(mongoTemplate.getCollectionNames())
                .contains("products")
                .doesNotContain("category")
                .doesNotContain("categories");
    }

    @Test
    void databaseCannotRefuseDeletingACategoryThatStillHasProductsBecauseThereIsNoConstraint() {
        String category = "s7-integrity-" + UUID.randomUUID().toString().substring(0, 8);
        ProductDto product = createProduct(category);

        // PostgreSQL throws a DataIntegrityViolationException here; MongoDB has no equivalent
        // guard - "removing the category" is just clearing a string and can never be rejected.
        assertThatCode(() -> mongoTemplate.getCollection("category").drop()).doesNotThrowAnyException();
        assertThat(productCountInCategory(category)).isEqualTo(1);

        // Deleting the product removes the last reference to the category, and the category ceases
        // to exist implicitly. No referential integrity check is involved at any point - it is the
        // application's responsibility, not the database's.
        productService.deleteProduct(product.getId());
        assertThat(productCountInCategory(category)).isEqualTo(0);
    }

    private ProductDto createProduct(String category) {
        return productService.createProduct(ProductCreateRequest.builder()
                .sku("S7-INTEGRITY-" + UUID.randomUUID().toString().substring(0, 8))
                .name("S7 integrity product")
                .category(category)
                .price(19.99)
                .stock(3)
                .attributes(Map.of("verified", true))
                .build());
    }

    private long productCountInCategory(String category) {
        return mongoTemplate.count(
                Query.query(Criteria.where("category").is(category)), ProductDocument.class);
    }
}
