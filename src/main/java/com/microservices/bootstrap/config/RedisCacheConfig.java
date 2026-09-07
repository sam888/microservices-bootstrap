package com.microservices.bootstrap.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.microservices.bootstrap.exception.handler.RedisCacheErrorHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;


import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author samuel.huang
 * Created: 15-June-2026
 *  
 * Redis cache configuration combining:
 *  - Default polymorphic typing via LaissezFaireSubTypeValidator (internal Redis only, VPC-protected)
 *  - Java 8 date/time support (JavaTimeModule, human-readable dates)
 *  - String keys, JSON values (readable via redis-cli)
 *  - Per-cache TTL overrides, configurable via application.yml
 *  - Null-value caching disabled
 *  - Transaction-aware cache manager (no caching on rollback)
 *  - Optional cache key prefix
 *  - RedisTemplate bean for manual/programmatic cache access
 *  
 *  Sample cache key stored by Redis for caching IdentService.getIdents(..):
 *   MS_BOOTSTRAP:members:get-details::moduleCode:DEMO:traderId:10800838383
 *   └──┬───────┘└──┬───────────────┘└┘└──────────────────────┬──────────────┘
 *    prefix        cache            ::          your SpEL-generated key
 *                  name            (separator)
 */
@Slf4j
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE)  // explicit default value, shown for clarity
public class RedisCacheConfig implements CachingConfigurer {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${cache.members.get-details.ttl-seconds:3600}")
    private long fetchIdentsTtlSeconds;

    @Value("${app.cache.key-prefix}")
    private String cacheKeyPrefix;

    private final RedisCacheErrorHandler redisCacheErrorHandler;

   public RedisCacheConfig(RedisCacheErrorHandler redisCacheErrorHandler) {
      this.redisCacheErrorHandler = redisCacheErrorHandler;
   }

   /**
     * Lettuce connection factory with lazy connection validation.
     * setValidateConnection(false) prevents startup failure if Redis is unavailable —
     * connection is only attempted on the first actual cache operation, not at startup.
     * Lettuce will auto-reconnect transparently when Redis recovers (e.g. after EKS pod restart).
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                // 100ms timeout only fires when Redis is down and the circuit is closed or 
                // during the circuit's HALF_OPEN probes
                .commandTimeout( Duration.ofMillis( 100 ) )
                .build();

        LettuceConnectionFactory factory =
                new LettuceConnectionFactory( serverConfig, clientConfig );
        factory.setValidateConnection( false );       // lazy connect — don't block startup
        return factory;
    }
    
    /**
     * Jackson ObjectMapper with default typing enabled for Redis serialization.
     * Uses LaissezFaireSubTypeValidator — the same default Spring Data Redis itself uses for GenericJackson2JsonRedisSerializer. 
     * This permits any @class type found in the cached JSON during deserialization, with no allowlist/denylist checking.
     * Safe here because Redis is internal-only within VPC, written only by this app, and cached object types are fixed 
     * by our own DTOs — no untrusted @class injection path exists for gadget-chain attacks.
     */ 
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();

        // Required by Jackson to serialize/deserialize LocalDateTime of cached DTO
        mapper.registerModule( new JavaTimeModule() );
        
        // Use ISO-8601 string format (e.g. "2023-05-11T00:00:00Z") for date/time fields instead of numeric timestamp 
        // arrays (e.g. [2023,5,11,0,0]) for LocalDateTime fields
        mapper.disable( SerializationFeature.WRITE_DATES_AS_TIMESTAMPS );

        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * RedisTemplate for manual/programmatic Redis access (in addition to @Cacheable).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory( connectionFactory );
        template.setKeySerializer( new StringRedisSerializer() );
        template.setHashKeySerializer( new StringRedisSerializer() );
        template.setValueSerializer( redisJsonSerializer );
        template.setHashValueSerializer( redisJsonSerializer );
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Default cache configuration applied to all caches unless overridden.
     */
    @Bean
    public RedisCacheConfiguration defaultCacheConfig(GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl( Duration.ofMinutes( 3 ) )   // Fallback TTL
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer( new StringRedisSerializer() ))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer( redisJsonSerializer ))
            .disableCachingNullValues();       // Never cache null — throw instead

        if (cacheKeyPrefix != null && !cacheKeyPrefix.isEmpty()) {
            log.info( "Prefix cache name with: {}", cacheKeyPrefix);
            config = config.prefixCacheNameWith( cacheKeyPrefix );
        }

        return config;
    }
    
    /**
     * CacheManager with per-cache TTL overrides.
     * Add one cacheConfigurations.put() entry per @Cacheable service method.
     * Cache name must match the value = CacheNames.XXX constant used in @Cacheable/@CacheEvict.
     * Warning: DO NOT cache volatile financial data (balances, transactions, etc.).
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration defaultCacheConfig,
            CircuitBreakerRegistry circuitBreakerRegistry) {

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(
            CacheNames.GET_MEMBER_DETAILS, // Cache name
            defaultCacheConfig.entryTtl( Duration.ofSeconds( fetchIdentsTtlSeconds ) )
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults( defaultCacheConfig )
            .withInitialCacheConfigurations( cacheConfigurations )
            .transactionAware()   // Honor Spring @Transactional (don't cache on rollback)
            .build();
    }

    /**
     * ReactiveRedisTemplate for non-blocking cache access in WebFlux services.
     * Required by ReactiveCacheSupport — the blocking RedisTemplate bean cannot be
     * used on Netty event loop threads without blocking them.
     *
     * Uses the same GenericJackson2JsonRedisSerializer as the blocking RedisTemplate
     * and CacheManager so all three share identical serialization behaviour and the
     * same keys are readable by all of them.
     */
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer) {

        RedisSerializationContext<String, Object> context =
                RedisSerializationContext.<String, Object>newSerializationContext(new StringRedisSerializer())
                        .value(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer))
                        .hashKey(new StringRedisSerializer())
                        .hashValue(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer))
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    /**
     * Handles Redis runtime errors gracefully — swallows exceptions so @Cacheable methods or methods calling
     * ReactiveCacheSupport.cachedMono(...) fall through to the actual DB/service call instead of propagating Redis errors to callers.
     * See RedisCacheErrorHandler for per-operation (GET/PUT/EVICT/CLEAR) log warnings.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return redisCacheErrorHandler;
    }

}
