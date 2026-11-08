package com.microservices.bootstrap.bank.consumer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Lets you confirm from Postman that the consumer really processed the events the producer wrote. */
@RestController
@RequestMapping("/demo/bank/events")
public class DomainEventController {

    private final DomainEventService events;

    public DomainEventController(DomainEventService events) {
        this.events = events;
    }

    @GetMapping
    public Flux<DomainEvent> list() {
        return events.recentEvents();
    }
}
