package com.benchmark.nosqlblocking.controller;

import com.benchmark.common.dto.ReportDtos.*;
import com.benchmark.nosqlblocking.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<CategorySalesResponse>> getSalesByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(reportService.getSalesByCategory(from, to));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductResponse>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.getTopProducts(from, to, limit));
    }

    /**
     * Lightweight date-range totals (no product/category join). Used by S4B to isolate the effect of
     * the {@code createdAt} index from the cost of {@code $lookup/$group} joins.
     */
    @GetMapping("/order-totals")
    public ResponseEntity<OrderTotalsResponse> getOrderTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(reportService.getOrderTotals(from, to));
    }

    @GetMapping("/category-monthly-summary")
    public ResponseEntity<List<CategoryMonthlySummaryResponse>> getCategoryMonthlySummary() {
        return ResponseEntity.ok(reportService.getCategoryMonthlySummary());
    }

    /** S5: join-heavy analytical report where the relational engine's optimizer excels. */
    @GetMapping("/category-revenue-detail")
    public ResponseEntity<List<CategoryRevenueDetailResponse>> getCategoryRevenueDetail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(reportService.getCategoryRevenueDetail(from, to));
    }
}
