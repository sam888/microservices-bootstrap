package com.microservices.bootstrap.config;

import com.microservices.bootstrap.exception.handler.RedisCacheErrorHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Records circuitBreaker.onSuccess()/onError() for the "redis" breaker on a fixed
 * schedule — the missing counterpart to RedisCacheErrorHandler, which only ever
 * calls onError().
 *
 * Deliberately does a real SET+GET through the SAME RedisTemplate/serializer used
 * by @Cacheable, rather than a bare PING. A PING only proves connectivity — it
 * would have stayed green throughout the InvalidTypeIdException/poisoned-key
 * incident this app already hit, since that failure was in JSON
 * (de)serialization, not connectivity. This probe exercises that same
 * serialization path so it can actually detect that class of failure too.
 */
@Slf4j
@Component
public class RedisHealthProbe {

   private static final String PROBE_KEY = "__health_probe__";

   private final RedisTemplate<String, Object> redisTemplate;
   private final CircuitBreaker circuitBreaker;

   public RedisHealthProbe(RedisTemplate<String, Object> redisTemplate,
                           CircuitBreakerRegistry circuitBreakerRegistry) {
      this.redisTemplate = redisTemplate;
      this.circuitBreaker = circuitBreakerRegistry.circuitBreaker( RedisCacheErrorHandler.CIRCUIT_BREAKER_NAME );
   }

   @Scheduled(fixedDelay = 5000)
   public void probe() {
      long start = System.nanoTime();
      try {
         redisTemplate.opsForValue().set(PROBE_KEY, "ok", Duration.ofSeconds(30));
         Object result = redisTemplate.opsForValue().get(PROBE_KEY);
         if (!"ok".equals(result)) {
            throw new IllegalStateException("Redis health probe round-trip returned unexpected value: " + result);
         }
         circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      } catch (Exception e) {
         circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
         log.warn("Redis health probe failed!");
      }
   }
}
