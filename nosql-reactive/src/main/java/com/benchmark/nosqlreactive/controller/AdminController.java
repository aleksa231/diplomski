package com.benchmark.nosqlreactive.controller;

import com.benchmark.common.dto.AdminDtos.CacheFlushResponse;
import com.benchmark.common.dto.AdminDtos.CacheToggleRequest;
import com.benchmark.common.dto.AdminDtos.CacheToggleResponse;
import com.benchmark.common.dto.AdminDtos.IndexToggleRequest;
import com.benchmark.common.dto.AdminDtos.IndexToggleResponse;
import com.benchmark.nosqlreactive.cache.CacheToggle;
import com.benchmark.nosqlreactive.cache.ReactiveCatalogCache;
import com.benchmark.nosqlreactive.service.IndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexService indexService;
    private final CacheToggle cacheToggle;
    private final ReactiveCatalogCache catalogCache;

    public AdminController(IndexService indexService,
                           CacheToggle cacheToggle,
                           ReactiveCatalogCache catalogCache) {
        this.indexService = indexService;
        this.cacheToggle = cacheToggle;
        this.catalogCache = catalogCache;
    }

    @PostMapping("/indexes")
    public Mono<ResponseEntity<IndexToggleResponse>> toggleIndexes(@RequestBody IndexToggleRequest request) {
        return indexService.toggleIndexes(request.isEnabled()).map(ResponseEntity::ok);
    }

    @PostMapping("/cache")
    public Mono<ResponseEntity<CacheToggleResponse>> toggleCache(@RequestBody CacheToggleRequest request) {
        cacheToggle.setEnabled(request.isEnabled());
        return Mono.just(ResponseEntity.ok(
                CacheToggleResponse.builder().enabled(cacheToggle.isEnabled()).build()));
    }

    @PostMapping("/cache/flush")
    public Mono<ResponseEntity<CacheFlushResponse>> flushCache() {
        return catalogCache.clear()
                .thenReturn(ResponseEntity.ok(CacheFlushResponse.builder().flushed(true).build()));
    }
}
