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
        @NotBlank
        private String customerRef;
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
    public static class OrderStatusUpdateRequest {
        @NotBlank
        private String status;
    }
}
