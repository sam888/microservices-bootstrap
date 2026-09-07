package com.microservices.bootstrap.service;

import com.microservices.bootstrap.config.CacheNames;
import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.MemberDetailsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;


/**
 * @author samuel.huang
 * Created: 5-September-2026
 */
@Slf4j
@Service
public class MemberService {

   private static final Long traderIdTestData = 10800838383L;

   public static final String CIRCUIT_BREAKER_NAME = "redis";

   private final CircuitBreaker circuitBreaker;

   public MemberService(CircuitBreakerRegistry circuitBreakerRegistry) {
      this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
   }

   /**
    * Returns Member Details for a given traderId, with Redis caching and circuit breaker protection.
    *
    * Normal flow (circuit CLOSED, Redis healthy):
    *   @Cacheable checks Redis first. On a hit the cached MemberDetailsResponseVO is returned immediately.
    *   On a miss the method body executes and the result is stored in Redis.
    *
    * Degraded flow (circuit OPEN, Redis down):
    *   After waitDurationInOpenState (default is 60s for Resilience4j), the breaker transitions to HALF_OPEN and allows probe
    *   calls through. A successful probe closes the circuit and normal caching resumes.
    */
   @Cacheable(
           value = CacheNames.GET_MEMBER_DETAILS,
           key = "'moduleCode:' + #moduleCode + ':traderId:' + #traderId",
           condition = "@memberService.isRedisCircuitClosed()"
   )
   public Mono<ApiResponseVO<MemberDetailsResponseVO>> getMemberDetails(String moduleCode, Long traderId) {
      return getMemberDetailsByDatabase(moduleCode, traderId);
   }


   /**
    * Core logic, shared by getMemberDetails() and getMemberDetailsFallback().
    *
    * Extracted so that neither the primary method nor the fallback duplicates business
    * logic. Both paths produce identical results — the only difference is whether the
    * result gets cached by @Cacheable on the way back out.
    */
   public Mono<ApiResponseVO<MemberDetailsResponseVO>> getMemberDetailsByDatabase(String moduleCode, Long traderId) {
      if ( !traderIdTestData.equals( traderId ) ) {
         return Mono.error(new InternalException( "-2", "No Member exists", null ));
      }

      MemberDetailsResponseVO responseVO = new MemberDetailsResponseVO();
      responseVO.setTraderId( traderId );
      responseVO.setFirstName( "John" );
      responseVO.setLastName( "Steward" );
      responseVO.setEmail( "john.steward@microservices.com" );
      responseVO.setPhoneNumber( "1234567890" );
      responseVO.setMemberStatusId( 1 );
      responseVO.success();

      log.info("Calling getMemberDetailsByDatabase(..) ...");

      // Simulate taking 500ms to process
      return Mono.just( new ApiResponseVO<>(responseVO) )
              .delayElement(Duration.ofMillis(500));

   }

   public boolean isRedisCircuitClosed() {
      return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
   }

}
