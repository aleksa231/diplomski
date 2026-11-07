package com.benchmark.runner.controller;

import com.benchmark.runner.model.LiveDtos.LiveStatus;
import com.benchmark.runner.service.LiveStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/live}: current rps, p95, cache hit ratio and service health (spec section 10.2).
 * The console polls this every two seconds.
 */
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveStatusService liveStatusService;

    public LiveController(LiveStatusService liveStatusService) {
        this.liveStatusService = liveStatusService;
    }

    @GetMapping
    public LiveStatus live() {
        return liveStatusService.current();
    }
}
