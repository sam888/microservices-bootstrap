package com.microservices.bootstrap.exception.handler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Handles Redis errors from Spring's @Cacheable / @CacheEvict annotations.
 *
 * Swallows all Redis exceptions so that a down Redis server never propagates
 * an error to callers — the @Cacheable method simply executes normally on error.
 *
 * Every error is also recorded into the Resilience4j "redis" CircuitBreaker.
 * This is the same instance that @CircuitBreaker(name = "redis") reads from —
 * so @Cacheable Redis failures and circuit breaker state are always
 * in sync. Once the failure rate threshold is reached the breaker trips OPEN.
 *
 * @author samuel.huang
 * Created: 16-June-2026
 * Updated: 18-June-2026 — Resilience4j circuit breaker integration
 */
@Slf4j
@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

    public static final String CIRCUIT_BREAKER_NAME = "redis";

    private final CircuitBreaker circuitBreaker;

    public RedisCacheErrorHandler(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker( CIRCUIT_BREAKER_NAME );
    }

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, e);
        log.warn("Redis GET failed for cache={} key={} — falling through to service", cache.getName(), key, e);
    }

    @Override                                   
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, e);
        log.warn("Redis PUT failed for cache={} key={} — continuing without caching", cache.getName(), key, e);
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, e);
        log.warn("Redis EVICT failed for cache={} key={}", cache.getName(), key, e);
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, e);
        log.warn("Redis CLEAR failed for cache={}", cache.getName(), e);
    }
}
