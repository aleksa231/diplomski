package com.benchmark.nosqlreactive.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Reactive replacement for the blocking Spring Cache configuration.
 *
 * <p>The Spring Cache abstraction is blocking (it wraps the value loader in a
 * {@code Callable}), so it cannot be used from a WebFlux application without blocking a
 * thread. Instead this module uses a dedicated {@link ReactiveRedisTemplate} whose values
 * are JSON-serialized exactly like the blocking {@code RedisCacheManager} did, and
 * {@link ReactiveCatalogCache} implements the get/put/clear/TTL semantics plus the
 * toggle, graceful degradation and instrumentation.
 */
@Configuration
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";

    public static final String CATALOG_TEMPLATE_BEAN = "catalogCacheRedisTemplate";

    @Bean(CATALOG_TEMPLATE_BEAN)
    public ReactiveRedisTemplate<String, Object> catalogCacheRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext()
                .key(RedisSerializer.string())
                .value(jsonSerializer())
                .hashKey(RedisSerializer.string())
                .hashValue(jsonSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    private static GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
