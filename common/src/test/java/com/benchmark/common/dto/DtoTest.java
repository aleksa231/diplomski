package com.benchmark.common.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    @Test
    void testProductDtoCreation() {
        ProductDto dto = ProductDto.builder()
                .id("test-id")
                .sku("LAP-001")
                .name("Laptop Pro")
                .category("laptops")
                .price(1299.99)
                .stock(10)
                .build();

        assertThat(dto.getId()).isEqualTo("test-id");
        assertThat(dto.getSku()).isEqualTo("LAP-001");
        assertThat(dto.getName()).isEqualTo("Laptop Pro");
        assertThat(dto.getPrice()).isEqualTo(1299.99);
        assertThat(dto.getStock()).isEqualTo(10);
    }

    @Test
    void testErrorResponseCreation() {
        ErrorResponse error = ErrorResponse.builder()
                .status(404)
                .code("NOT_FOUND")
                .message("Resource not found")
                .path("/api/products/123")
                .build();

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("NOT_FOUND");
        assertThat(error.getMessage()).isEqualTo("Resource not found");
        assertThat(error.getPath()).isEqualTo("/api/products/123");
        assertThat(error.getTimestamp()).isNotNull();
    }
}
