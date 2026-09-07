package com.microservices.bootstrap.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive cache-aside utility for WebFlux services.
 *
 * Why this exists instead of @Cacheable:
 *   Spring's @Cacheable is synchronous — it intercepts the return value after the method returns,
 *   then serializes it to Redis. When the return type is Mono<T>, the interceptor sees the Mono
 *   wrapper, not the T inside it, and either caches the wrong thing or throws a serialization error
 *   (swallowed silently by RedisCacheErrorHandler). Result: zero caching, no crash, no warning.
 *
 *   This class implements cache-aside pattern reactively: check Redis first, on a miss execute the upstream
 *   Mono, store the result, then return it — all without ever blocking a Netty event loop thread.
 *
 * Usage — inject this bean and call cachedMono():
 * <pre>
 *   String key = CacheNames.FETCH_IDENTS + "::" + moduleCode + ":" + email + ":" + mobileNumber;
 *   return reactiveCacheSupport.cachedMono(key, fetchFromUpstream(), IdentsResponseDTO.class, ttl);
 * </pre>
 *
 * Key format should follow the project standard (see CacheNames.java):
 *   <keyPrefix>:<cacheName>::<param1>:<param2>:...
 *   e.g. MS_BOOTSTRAP:members:get-details::moduleCode:DEMO:traderId:10800838383
 *
 * Redis errors (GET or SET) are caught and logged; the upstream Mono is called as a fallback
 * so callers are never impacted by Redis availability — consistent with RedisCacheErrorHandler.
 *
 * @author samuel.huang
 * Created: 17-June-2026
 */
@Slf4j
@Component
public class ReactiveCacheSupport {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final ObjectMapper objectMapper; 

    public ReactiveCacheSupport(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
                                ObjectMapper objectMapper) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Cache-aside pattern for a single value.
     *
     * Flow:
     *   1. GET from Redis. On hit, deserialize and return immediately.
     *   2. On miss (empty), subscribe to {@code upstream} to fetch the real value.
     *   3. SET the result in Redis with the given TTL (fire-and-forget, non-blocking).
     *   4. If Redis GET or SET throws, log a warning and fall through to {@code upstream}
     *      so the caller always gets a response regardless of Redis health.
     *
     * @param key        Full Redis key, including prefix and cache name.
     *                   Follow the project key format: {@code <prefix><cacheName>::<params>}
     * @param upstream   The Mono to execute on a cache miss (e.g. an HTTP call via WebClient).
     *                   Must be cold (not yet subscribed) — pass a method reference or lambda,
     *                   not an already-executing Mono, so it is only subscribed on an actual miss.
     * @param type       The concrete class to deserialize the cached value into.
     *                   Must match the type returned by {@code upstream}.
     * @param ttl        How long the entry lives in Redis. Use the same value as the
     *                   RedisCacheConfig.cacheManager() per-cache TTL for consistency.
     * @param <T>        The payload type being cached.
     * @return           Mono emitting the cached or freshly fetched value.
     */
    public <T> Mono<T> cachedMono(String key, Mono<T> upstream, Class<T> type, Duration ttl) {
        return reactiveRedisTemplate.opsForValue()
            .get(key)
            .flatMap(raw -> deserialize(raw, type, key))   // hit: deserialize data from Redis and return
            .switchIfEmpty(                                // miss: fetch data from core API, return
                upstream.flatMap(value -> store(key, value, ttl))
            )
            .onErrorResume(ex -> {                        // Redis GET failed
                log.warn("Redis GET failed for key={} — falling through to upstream. Cause: {}",
                        key, ex.toString());
                return upstream;
            });
    }

    /**
     * Evicts a single key from Redis (e.g. called from a @CacheEvict-equivalent method).
     * Errors are logged and swallowed — consistent with RedisCacheErrorHandler.evict behaviour.
     *
     * @param key  The exact Redis key to delete.
     * @return     Mono<Void> that completes when eviction is done (or on error, after logging).
     */
    public Mono<Void> evict(String key) {
        return reactiveRedisTemplate.opsForValue()
            .delete(key)
            .doOnSuccess(deleted -> {
                if (Boolean.TRUE.equals(deleted)) {
                    log.debug("Redis EVICT succeeded for key={}", key);
                } else {
                    log.debug("Redis EVICT: key not found (already expired or never cached): key={}", key);
                }
            })
            .onErrorResume(ex -> {
                log.warn("Redis EVICT failed for key={} — continuing. Cause: {}", key, ex.toString());
                return Mono.empty();
            })
            .then();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Deserializes a raw Redis value into the target type.
     *
     * ReactiveRedisTemplate returns Object from get(); the stored JSON was written by
     * GenericJackson2JsonRedisSerializer with activateDefaultTyping, which embeds a @class
     * field so Jackson knows the concrete type. This means the raw object may already be the
     * correct type (if Redis deserialized it on read), or it may be a LinkedHashMap if type
     * information was lost. convertValue() handles both cases cleanly.
     *
     * Returns Mono.empty() if conversion fails so switchIfEmpty() falls through to upstream.
     */
    private <T> Mono<T> deserialize(Object raw, Class<T> type, String key) {
        try {
            T value = objectMapper.convertValue(raw, type);
            log.debug("Redis cache HIT for key={}", key);
            return Mono.just(value);
        } catch (Exception ex) {
            // Stale or incompatible cached data (e.g. after a DTO refactor).
            // Treat as a miss so upstream is called and fresh data is stored.
            log.warn("Redis cache HIT but deserialization failed for key={} targetType={} — treating as miss. Cause: {}",
                    key, type.getSimpleName(), ex.toString());
            return Mono.empty();
        }
    }

    /**
     * Writes a value to Redis and returns it unchanged so it can be passed downstream.
     * SET errors are logged and swallowed — the caller still receives the value, it just
     * won't be cached (same behaviour as RedisCacheErrorHandler.handleCachePutError).
     */
    private <T> Mono<T> store(String key, T value, Duration ttl) {
        return reactiveRedisTemplate.opsForValue()
            .set(key, value, ttl)
            .doOnSuccess(ok -> log.debug("Redis cache SET key={} ttl={}s", key, ttl.toSeconds()))
            .onErrorResume(ex -> {
                log.warn("Redis SET failed for key={} — continuing without caching. Cause: {}",
                        key, ex.toString());
                return Mono.just(false);    // swallow; thenReturn(value) still runs
            })
            .thenReturn(value);
    }
}
