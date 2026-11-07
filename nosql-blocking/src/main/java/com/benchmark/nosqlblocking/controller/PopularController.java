package com.benchmark.nosqlblocking.controller;

import com.benchmark.common.dto.ReportDtos.PopularItemResponse;
import com.benchmark.nosqlblocking.service.PopularityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/popular")
public class PopularController {

    private final PopularityService popularityService;

    public PopularController(PopularityService popularityService) {
        this.popularityService = popularityService;
    }

    @GetMapping
    public List<PopularItemResponse> popular(@RequestParam(defaultValue = "views") String type,
                                             @RequestParam(defaultValue = "10") int limit) {
        return popularityService.top(type, limit);
    }
}
