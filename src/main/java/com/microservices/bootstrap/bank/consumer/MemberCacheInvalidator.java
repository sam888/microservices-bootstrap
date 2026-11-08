package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.bootstrap.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Turns a raw Debezium change event for the member table into a Redis cache eviction.
 *
 * Eviction is idempotent, so Kafka's at-least-once delivery is harmless. Redis errors are deliberately NOT
 * swallowed here (unlike the @Cacheable read path): if eviction fails we must retry and eventually dead-letter,
 * otherwise the cache would silently keep serving stale data.
 */
@Slf4j
@Component
public class MemberCacheInvalidator {

    private final ObjectMapper mapper;
    private final CacheManager cacheManager;

    public MemberCacheInvalidator(ObjectMapper mapper, CacheManager cacheManager) {
        this.mapper = mapper;
        this.cacheManager = cacheManager;
    }

    /** @return true if a cache entry was targeted for eviction, false if the event was ignored. */
    public boolean handle(String json) {
        if (json == null) {
            return false; // tombstone record
        }
        MemberChangeEvent event;
        try {
            event = mapper.readValue(json, MemberChangeEvent.class);
        } catch (JsonProcessingException e) {
            // Poison pill: non-retryable, goes straight to the dead-letter topic.
            throw new IllegalArgumentException("Malformed CDC event", e);
        }
        if (event.op() == null) {
            throw new IllegalArgumentException("CDC event without op");
        }

        MemberChangeEvent.Row row = switch (event.op()) {
            case "c", "r", "u" -> event.after();
            case "d" -> event.before();
            default -> null;
        };
        if (row == null || row.id() == null) {
            log.warn("Ignoring CDC event op={} (unsupported op or no row id)", event.op());
            return false;
        }

        Cache cache = cacheManager.getCache(CacheNames.GET_BANK_MEMBER_PROFILE);
        if (cache == null) {
            return false;
        }
        cache.evict(MemberProfileService.cacheKey(row.id()));
        log.info("op={} evicted cached profile of member id={}", event.op(), row.id());
        return true;
    }
}
