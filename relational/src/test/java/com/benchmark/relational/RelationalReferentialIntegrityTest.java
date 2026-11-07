package com.benchmark.relational;

import com.benchmark.common.dto.ProductCreateRequest;
import com.benchmark.common.dto.ProductDto;
import com.benchmark.relational.store.ProductStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S7 (b) - referential integrity.
 *
 * <p>This is a correctness property of the storage engine, not a load characteristic, so nothing
 * meaningful can be measured with k6; it is asserted here instead. See docs/s7-integrity.md.
 *
 * <p>Normalized (EAV) variant: {@code product.category_id} is declared
 * {@code REFERENCES category(id) ON DELETE RESTRICT} in V1__init_schema.sql, so PostgreSQL itself
 * refuses to delete a category that still has products. MongoDB cannot express that constraint -
 * see {@code NosqlReferentialIntegrityTest} in the nosql-blocking module.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RelationalReferentialIntegrityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.relational.variant", () -> "normalized");
        registry.add("app.cache.enabled", () -> "false");
    }

    @Autowired
    private ProductStore productStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void categoryWithProductsIsProtectedByTheForeignKeyAndCanBeDeletedAfterwards() {
        ProductDto product = productStore.create(ProductCreateRequest.builder()
                .sku("S7-INTEGRITY-" + UUID.randomUUID().toString().substring(0, 8))
                .name("S7 integrity product")
                .category("s7-integrity")
                .price(19.99)
                .stock(3)
                .attributes(Map.of("verified", true))
                .build());

        String categoryId = categoryIdOf(product.getId());
        assertThat(categoryId).isNotBlank();
        assertThat(countCategories(categoryId)).isEqualTo(1);

        // The database rejects the delete; the application never has to remember to check.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countCategories(categoryId)).isEqualTo(1);

        // Removing the referencing product unblocks the category.
        assertThat(productStore.deleteById(product.getId())).isTrue();
        assertThat(jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId)).isEqualTo(1);
    }

    private String categoryIdOf(String productId) {
        return jdbcTemplate.queryForObject("SELECT category_id FROM product WHERE id = ?", String.class, productId);
    }

    private int countCategories(String categoryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?", Integer.class, categoryId);
        return count == null ? 0 : count;
    }
}
