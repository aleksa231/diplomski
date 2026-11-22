package com.benchmark.relational.controller;

import com.benchmark.common.dto.AdminDtos.CacheFlushResponse;
import com.benchmark.common.dto.AdminDtos.CacheToggleRequest;
import com.benchmark.common.dto.AdminDtos.CacheToggleResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleRequest;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.benchmark.relational.cache.CacheConfig;
import com.benchmark.relational.cache.CacheToggle;
import com.benchmark.relational.service.IndexService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexService indexService;
    private final CacheToggle cacheToggle;
    private final CacheManager cacheManager;

    public AdminController(IndexService indexService,
                           CacheToggle cacheToggle,
                           CacheManager cacheManager) {
        this.indexService = indexService;
        this.cacheToggle = cacheToggle;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/indexes")
    public ResponseEntity<IndexToggleResponse> toggleIndexes(@RequestBody IndexToggleRequest request) {
        return ResponseEntity.ok(indexService.toggleIndexes(request.isEnabled()));
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
