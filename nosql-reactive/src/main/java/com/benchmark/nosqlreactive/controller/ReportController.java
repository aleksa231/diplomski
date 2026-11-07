package com.benchmark.nosqlreactive.controller;

import com.benchmark.common.dto.ReportDtos.CategoryMonthlySummaryResponse;
import com.benchmark.common.dto.ReportDtos.CategorySalesResponse;
import com.benchmark.common.dto.ReportDtos.TopProductResponse;
import com.benchmark.nosqlreactive.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/sales-by-category")
    public Mono<ResponseEntity<List<CategorySalesResponse>>> getSalesByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.getSalesByCategory(from, to).map(ResponseEntity::ok);
    }

    @GetMapping("/top-products")
    public Mono<ResponseEntity<List<TopProductResponse>>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.getTopProducts(from, to, limit).map(ResponseEntity::ok);
    }

    /** Lightweight date-range totals (no product/category join), used by S4B to isolate index impact. */
    @GetMapping("/order-totals")
    public Mono<ResponseEntity<com.benchmark.common.dto.ReportDtos.OrderTotalsResponse>> getOrderTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.getOrderTotals(from, to).map(ResponseEntity::ok);
    }

    @GetMapping("/category-monthly-summary")
    public Mono<ResponseEntity<List<CategoryMonthlySummaryResponse>>> getCategoryMonthlySummary() {
        return reportService.getCategoryMonthlySummary().map(ResponseEntity::ok);
    }
}
