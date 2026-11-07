package com.benchmark.nosqlblocking.controller;

import com.benchmark.common.dto.AdminDtos.CacheFlushResponse;
import com.benchmark.common.dto.AdminDtos.CacheToggleRequest;
import com.benchmark.common.dto.AdminDtos.CacheToggleResponse;
import com.benchmark.common.dto.AdminDtos.ExplainResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleRequest;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.benchmark.nosqlblocking.cache.CacheConfig;
import com.benchmark.nosqlblocking.cache.CacheToggle;
import com.benchmark.nosqlblocking.service.IndexAndExplainService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexAndExplainService indexAndExplainService;
    private final CacheToggle cacheToggle;
    private final CacheManager cacheManager;

    public AdminController(IndexAndExplainService indexAndExplainService,
                           CacheToggle cacheToggle,
                           CacheManager cacheManager) {
        this.indexAndExplainService = indexAndExplainService;
        this.cacheToggle = cacheToggle;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/indexes")
    public ResponseEntity<IndexToggleResponse> toggleIndexes(@RequestBody IndexToggleRequest request) {
        return ResponseEntity.ok(indexAndExplainService.toggleIndexes(request.isEnabled()));
    }

    @GetMapping("/explain")
    public ResponseEntity<ExplainResponse> explain(@RequestParam String query) {
        return ResponseEntity.ok(indexAndExplainService.explain(query));
    }

    @PostMapping("/cache")
    public ResponseEntity<CacheToggleResponse> toggleCache(@RequestBody CacheToggleRequest request) {
        cacheToggle.setEnabled(request.isEnabled());
        return ResponseEntity.ok(CacheToggleResponse.builder().enabled(cacheToggle.isEnabled()).build());
    }

    @PostMapping("/cache/flush")
    public ResponseEntity<CacheFlushResponse> flushCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
        return ResponseEntity.ok(CacheFlushResponse.builder().flushed(true).build());
    }
}
