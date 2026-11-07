package com.benchmark.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySalesResponse {
        private String category;
        private Long orderCount;
        private Double totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProductResponse {
        private String productId;
        private String productName;
        private String category;
        private Long unitsSold;
        private Double revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryMonthlySummaryResponse {
        private String category;
        private Integer year;
        private Integer month;
        private Double revenue;
        private Long orderCount;
        private Double averageBasketSize;
        @Builder.Default
        private List<TopProductResponse> topProducts = new ArrayList<>();
    }

    /** Lightweight date-range totals (no product/category join) used to isolate index impact in S4B. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderTotalsResponse {
        private Long orderCount;
        private Double totalRevenue;
        private Long totalUnits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopularItemResponse {
        private String productId;
        private Double score;
    }
}
