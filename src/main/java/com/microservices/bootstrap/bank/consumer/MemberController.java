package com.microservices.bootstrap.bank.consumer;

import com.microservices.bootstrap.bank.OnboardRequestDTO;
import com.microservices.bootstrap.bank.OnboardResponseDTO;
import com.microservices.bootstrap.bank.producer.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * All member endpoints of the demo, under /demo/bank/members:
 *
 *   POST /demo/bank/members                 onboard a member (producer side: OnboardingService -> onboard_member(...),
 *                                           which writes member + account + outbox row in one transaction)
 *   GET  /demo/bank/members/{id}/profile    cached read (consumer side: MemberProfileService, Redis in front of
 *                                           Postgres, kept fresh by the CDC consumer)
 *
 * It follows the same pattern as the project's own MemberController (GET /members/{traderId}, in package
 * com.microservices.bootstrap.controller): the controller only delegates. HTTP only: all data access is in the
 * services, so the controller never touches the database (LayeringTest checks this).
 *
 * The class sits in the consumer package because the cached read belongs next to the code that evicts the cache.
 * Onboarding is producer-side work, so it is delegated to producer.OnboardingService; if the two sides are ever split
 * into separate services, POST /members moves with the producer.
 *
 * Mounted under /demo so JWTFilter lets it through without a token (same convention as BankDemoController).
 * The bean name is explicit because the project already has a bean called "memberController"; two beans with the
 * same default name would stop the application from starting.
 *
 * Try it: call the profile twice (the second call is a cache hit, no "Cache miss" line in the log), change the row
 * with UPDATE member SET address = '99 New Rd' WHERE id = 1 in pgAdmin, wait a second for Debezium, and call it
 * again: the consumer has evicted the entry, so the new address is read from Postgres.
 */
@RestController("bankMemberController")
@RequestMapping("/demo/bank/members")
public class MemberController {

    private final OnboardingService onboarding;
    private final MemberProfileService profiles;

    public MemberController(OnboardingService onboarding, MemberProfileService profiles) {
        this.onboarding = onboarding;
        this.profiles = profiles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OnboardResponseDTO> onboard(@Valid @RequestBody OnboardRequestDTO request) {
        return onboarding.onboard(request);
    }

    @GetMapping("/{memberId}/profile")
    public Mono<MemberProfile> getMemberProfile(@PathVariable long memberId) {
        return profiles.getProfile(memberId);
    }
}
