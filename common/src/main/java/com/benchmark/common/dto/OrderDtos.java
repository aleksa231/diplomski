package com.benchmark.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreateRequest {
        @NotBlank
        private String cartId;
        /**
         * Kept for compatibility with the seeded dataset and the load tests. The storefront sends
         * the composed "Name &lt;email&gt;" form, while {@code customerName} and
         * {@code customerEmail} carry the same information as separate fields.
         */
        @NotBlank
        private String customerRef;
        private String customerName;
        private String customerEmail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private String productId;
        private String productName;
        private Double price;
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusHistoryDto {
        private String status;
        @Builder.Default
        private Instant timestamp = Instant.now();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private String id;
        private String customerRef;
        private String customerName;
        private String customerEmail;
        @Builder.Default
        private List<OrderItemDto> items = new ArrayList<>();
        private Double total;
        private String status;
        @Builder.Default
        private List<OrderStatusHistoryDto> statusHistory = new ArrayList<>();
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageOrderResponse {
        private List<OrderResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusUpdateRequest {
        @NotBlank
        private String status;
    }
}
