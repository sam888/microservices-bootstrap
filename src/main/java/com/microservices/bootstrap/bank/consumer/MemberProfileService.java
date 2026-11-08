package com.microservices.bootstrap.bank.consumer;

import com.microservices.bootstrap.bank.producer.BankExceptions.NotFoundException;
import com.microservices.bootstrap.config.CacheNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Read side with a Redis cache in front of Postgres (function get_member_profile). Called by MemberController.
 *
 * The cache has a TTL, but TTL alone means up to N minutes of stale data after a change. The CDC consumer
 * (see MemberCacheInvalidator) evicts the entry as soon as Debezium reports that the member row changed,
 * no matter WHO changed it: this API, another service, or someone running UPDATE in psql.
 *
 * Same shape as MemberService.getMemberDetails(..) in this project: @Cacheable inside, the shared "redis"
 * circuit breaker outside. Normal flow: Redis hit returns immediately, a miss runs the query and stores the result.
 * Degraded flow (Redis down, circuit OPEN): the fallback queries Postgres directly with no Redis I/O at all.
 */
@Slf4j
@Service
public class MemberProfileService {

    public static final String CIRCUIT_BREAKER_NAME = "redis";

    /** Must produce the same key as the SpEL expression in @Cacheable below. Used by the CDC consumer to evict. */
    public static String cacheKey(long memberId) {
        return "member:" + memberId;
    }

    private final DatabaseClient db;

    public MemberProfileService(DatabaseClient db) {
        this.db = db;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getProfileFallback")
    @Cacheable(value = CacheNames.GET_BANK_MEMBER_PROFILE, key = "'member:' + #memberId")
    public Mono<MemberProfile> getProfile(long memberId) {
        return loadFromDatabase(memberId);
    }

    /**
     * Resilience4j fallback: the original parameters plus a trailing Throwable. Fires when the circuit is OPEN or when
     * an error leaks past @Cacheable. A "member not found" is a normal answer, not a Redis problem, so it is passed
     * on unchanged instead of querying the database a second time.
     */
    public Mono<MemberProfile> getProfileFallback(long memberId, Throwable cause) {
        if (cause instanceof NotFoundException) {
            return Mono.error(cause);
        }
        log.warn("Circuit breaker fallback for member {}: reading Postgres directly ({})", memberId, cause.toString());
        return loadFromDatabase(memberId);
    }

    /** Shared by the primary method and the fallback, so neither duplicates the query. */
    Mono<MemberProfile> loadFromDatabase(long memberId) {
        return db.sql("SELECT * FROM get_member_profile(:memberId)")
                .bind("memberId", memberId)
                .map((row, meta) -> new MemberProfile(
                        row.get("id", Long.class),
                        row.get("first_name", String.class),
                        row.get("last_name", String.class),
                        row.get("email", String.class),
                        row.get("address", String.class),
                        row.get("member_status_id", Short.class)))
                .one()
                .doOnSubscribe(s -> log.info("Cache miss: loading member {} from Postgres (R2DBC)", memberId))
                .switchIfEmpty(Mono.error(new NotFoundException("Member " + memberId + " not found")));
    }
}
