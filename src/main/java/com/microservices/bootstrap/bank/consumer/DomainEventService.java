package com.microservices.bootstrap.bank.consumer;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Read side of the inbox for the REST API. Controllers call this instead of the store. It shows the events recorded
 * by the event-log consumer (bootstrap-domain-events); other consumers' rows are in the same table (see pgAdmin).
 */
@Service
public class DomainEventService {

    private static final int MAX_EVENTS = 100;

    private final DomainEventStore store;

    public DomainEventService(DomainEventStore store) {
        this.store = store;
    }

    /** The events the event-log consumer processed most recently, newest first, at most 100. */
    public Flux<DomainEvent> recentEvents() {
        return store.findRecent(DomainEventHandler.CONSUMER, MAX_EVENTS);
    }
}
