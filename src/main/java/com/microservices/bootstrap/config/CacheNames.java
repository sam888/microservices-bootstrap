package com.microservices.bootstrap.config;

/**
 * @author samuel.huang
 * Created: 6-Sep-2026
 *
 * Central registry of Redis cache name constants. Each constant is used in @Cacheable/@CacheEvict annotations and
 * registered in RedisCacheConfig.cacheManager() for per-cache TTL configuration.
 *
 * Constant naming standard: <entity>:<operation> — all lowercase, hyphen to separate words.
 * e.g. "idents:fetch", "member:enquiry", "card:fetch-by-id"
 *
 * Redis key format: <keyPrefix>:<cacheName>::<SpEL-generated-key>
 * e.g. MS_BOOTSTRAP:members:get-details::moduleCode:DEMO:traderId:10800838383
 */
public class CacheNames {

   // Caches MemberService.getMemberDetails(..)
   public static final String GET_MEMBER_DETAILS = "members:get-details";

   // Caches MemberProfileService.getProfile(..) (bank CDC demo). Evicted by MemberCacheInvalidator when Debezium
   // reports a change to the member table.
   public static final String GET_BANK_MEMBER_PROFILE = "bank-members:get-profile";
}
