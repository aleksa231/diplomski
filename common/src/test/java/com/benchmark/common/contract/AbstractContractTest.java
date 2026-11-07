package com.benchmark.common.contract;

import com.benchmark.common.dto.*;
import com.benchmark.common.dto.CartDtos.CartItemRequest;
import com.benchmark.common.dto.CartDtos.CartResponse;
import com.benchmark.common.dto.OrderDtos.OrderCreateRequest;
import com.benchmark.common.dto.OrderDtos.OrderResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractContractTest {

    protected WebTestClient client;

    protected void initClient(String baseUrl) {
        this.client = WebTestClient.bindToServer()
                .baseUrl(baseUrl)
                .build();
    }

    @Test
    @Order(1)
    void testProductCrudAndLifecycle() {
        String sku = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        ProductCreateRequest createReq = ProductCreateRequest.builder()
                .sku(sku)
                .name("Ultra Gaming Laptop")
                .category("laptops")
                .price(1499.99)
                .stock(25)
                .attributes(Map.of("cpu", "AMD Ryzen 7", "ramGb", 32, "gpu", "RTX 4070"))
                .build();

        // 1. Create
        ProductDto created = client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getSku()).isEqualTo(sku);
        assertThat(created.getPrice()).isEqualTo(1499.99);
        String productId = created.getId();

        // 2. Read by ID
        ProductDto fetched = client.get().uri("/api/products/" + productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(productId);
        assertThat(fetched.getAttributes()).containsEntry("ramGb", 32);

        // 3. Update
        createReq.setPrice(1399.99);
        createReq.setStock(20);
        ProductDto updated = client.put().uri("/api/products/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();

        assertThat(updated.getPrice()).isEqualTo(1399.99);
        assertThat(updated.getStock()).isEqualTo(20);

        // 4. Add Review
        ReviewCreateRequest reviewReq = ReviewCreateRequest.builder()
                .rating(5)
                .text("Excellent build quality and battery life")
                .build();

        ProductDto reviewed = client.post().uri("/api/products/" + productId + "/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reviewReq)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();

        assertThat(reviewed.getReviewCount()).isGreaterThanOrEqualTo(1);
        assertThat(reviewed.getRatingAvg()).isGreaterThan(0.0);
        assertThat(reviewed.getReviews()).isNotEmpty();

        // 5. Delete
        client.delete().uri("/api/products/" + productId)
                .exchange()
                .expectStatus().isNoContent();

        // 6. Verify 404
        ErrorResponse err = client.get().uri("/api/products/" + productId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult().getResponseBody();

        assertThat(err).isNotNull();
        assertThat(err.getStatus()).isEqualTo(404);
        assertThat(err.getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    @Order(2)
    void testDynamicAttributeFiltering() {
        String sku16 = "FILTER-16-" + UUID.randomUUID().toString().substring(0, 8);
        String sku64 = "FILTER-64-" + UUID.randomUUID().toString().substring(0, 8);

        client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ProductCreateRequest.builder()
                        .sku(sku16)
                        .name("Laptop Sixteen")
                        .category("laptops")
                        .price(899.00)
                        .stock(10)
                        .attributes(Map.of("ramGb", 16, "cpu", "Intel Core i7"))
                        .build())
                .exchange().expectStatus().isCreated();

        client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ProductCreateRequest.builder()
                        .sku(sku64)
                        .name("Laptop SixtyFour")
                        .category("laptops")
                        .price(1899.00)
                        .stock(5)
                        .attributes(Map.of("ramGb", 64, "cpu", "Intel Core i9"))
                        .build())
                .exchange().expectStatus().isCreated();

        // Query by category and dynamic attr.ramGb=16
        PageProductResponse resp = client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/products")
                        .queryParam("category", "laptops")
                        .queryParam("attr.ramGb", "16")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PageProductResponse.class)
                .returnResult().getResponseBody();

        assertThat(resp).isNotNull();
        assertThat(resp.getContent()).anyMatch(p -> p.getSku().equals(sku16));
        assertThat(resp.getContent()).noneMatch(p -> p.getSku().equals(sku64));
    }

    @Test
    @Order(3)
    void testSearchAndBulkInsert() {
        String prefix = "BULK-" + UUID.randomUUID().toString().substring(0, 6);
        List<ProductCreateRequest> batch = List.of(
                ProductCreateRequest.builder().sku(prefix + "-1").name(prefix + " Alpha").category("books").price(20.0).stock(100).build(),
                ProductCreateRequest.builder().sku(prefix + "-2").name(prefix + " Beta").category("books").price(25.0).stock(100).build()
        );

        client.post().uri("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(batch)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.insertedCount").isEqualTo(2);

        PageProductResponse searchResp = client.get()
                .uri("/api/products/search?q=" + prefix)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PageProductResponse.class)
                .returnResult().getResponseBody();

        assertThat(searchResp).isNotNull();
        assertThat(searchResp.getContent()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(4)
    void testReportsEndpoints() {
        client.get().uri("/api/reports/sales-by-category")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class);

        client.get().uri("/api/reports/top-products?limit=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class);

        client.get().uri("/api/reports/category-monthly-summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class);
    }

    @Test
    @Order(5)
    void testAdminExplainAndIndexes() {
        client.post().uri("/api/admin/indexes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("enabled", true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.indexesEnabled").isEqualTo(true);

        client.get().uri("/api/admin/explain?query=Q1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.query").isEqualTo("Q1")
                .jsonPath("$.plan").isNotEmpty();
    }

    @Test
    @Order(6)
    void testCartLifecycle() {
        String cartId = "cart-" + UUID.randomUUID();
        String productId = createProduct("CART", "laptops", 500.0, 10);

        // Empty cart
        CartResponse empty = client.get().uri("/api/carts/" + cartId + "/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult().getResponseBody();
        assertThat(empty).isNotNull();
        assertThat(empty.getItems()).isEmpty();

        // Add
        CartResponse added = client.post().uri("/api/carts/" + cartId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CartItemRequest.builder().productId(productId).quantity(2).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult().getResponseBody();
        assertThat(added.getItems()).hasSize(1);
        assertThat(added.getItems().get(0).getQuantity()).isEqualTo(2);

        // Patch
        CartResponse patched = client.patch().uri("/api/carts/" + cartId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CartItemRequest.builder().productId(productId).quantity(5).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult().getResponseBody();
        assertThat(patched.getItems().get(0).getQuantity()).isEqualTo(5);

        // Delete single item
        CartResponse removed = client.delete().uri("/api/carts/" + cartId + "/items?productId=" + productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult().getResponseBody();
        assertThat(removed.getItems()).isEmpty();
    }

    @Test
    @Order(7)
    void testOrderFlowDecrementsStock() {
        String cartId = "order-" + UUID.randomUUID();
        String productId = createProduct("ORDER", "phones", 300.0, 5);

        client.post().uri("/api/carts/" + cartId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CartItemRequest.builder().productId(productId).quantity(2).build())
                .exchange().expectStatus().isOk();

        OrderResponse order = client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(OrderCreateRequest.builder().cartId(cartId).customerRef("cust-1").build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order).isNotNull();
        assertThat(order.getId()).isNotBlank();
        assertThat(order.getTotal()).isEqualTo(600.0);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getStatus()).isEqualTo("CREATED");

        // Stock decremented from 5 to 3
        ProductDto product = client.get().uri("/api/products/" + productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();
        assertThat(product.getStock()).isEqualTo(3);

        // Cart is cleared after checkout
        CartResponse cart = client.get().uri("/api/carts/" + cartId + "/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult().getResponseBody();
        assertThat(cart.getItems()).isEmpty();

        // Status transition appends to the history
        OrderResponse updated = client.patch().uri("/api/orders/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "SHIPPED"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();
        assertThat(updated.getStatus()).isEqualTo("SHIPPED");
        assertThat(updated.getStatusHistory()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(8)
    void testInsufficientStockReturnsConflict() {
        String cartId = "conflict-" + UUID.randomUUID();
        String productId = createProduct("CONFLICT", "books", 15.0, 1);

        client.post().uri("/api/carts/" + cartId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CartItemRequest.builder().productId(productId).quantity(2).build())
                .exchange().expectStatus().isOk();

        ErrorResponse err = client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(OrderCreateRequest.builder().cartId(cartId).customerRef("cust-2").build())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .returnResult().getResponseBody();

        assertThat(err).isNotNull();
        assertThat(err.getCode()).isEqualTo("CONFLICT");

        // Stock must not have gone negative
        ProductDto product = client.get().uri("/api/products/" + productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    @Order(9)
    void testPopularityRanking() {
        String productId = createProduct("POPULAR", "groceries", 5.0, 100);

        // A read increments the views sorted set
        client.get().uri("/api/products/" + productId).exchange().expectStatus().isOk();

        List<?> popular = client.get().uri("/api/popular?type=views&limit=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult().getResponseBody();

        assertThat(popular).isNotNull();
        assertThat(popular).isNotEmpty();
    }

    @Test
    @Order(10)
    void testCacheAdminToggle() {
        client.post().uri("/api/admin/cache")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("enabled", false))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);

        client.post().uri("/api/admin/cache/flush")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.flushed").isEqualTo(true);

        client.post().uri("/api/admin/cache")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("enabled", true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true);
    }

    private String createProduct(String skuPrefix, String category, double price, int stock) {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku(skuPrefix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .name(skuPrefix + " Product")
                .category(category)
                .price(price)
                .stock(stock)
                .attributes(Map.of("sampleAttr", "value"))
                .build();

        ProductDto created = client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();
        return created.getId();
    }
}
