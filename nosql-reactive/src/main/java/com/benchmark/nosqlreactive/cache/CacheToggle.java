package com.benchmark.nosqlreactive.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime switch for the Redis catalog cache (app.cache.enabled). Flipped through
 * {@code POST /api/admin/cache}.
 */
@Component
public class CacheToggle {

    private final AtomicBoolean enabled;

    public CacheToggle(@Value("${app.cache.enabled:true}") boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public boolean check() {
        return enabled.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
