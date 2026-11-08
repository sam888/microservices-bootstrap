package com.microservices.bootstrap.bank.consumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The inbox: remembers which business events each consumer has already processed (table "inbox", see
 * db/07_inbox.sql), so that Kafka's at-least-once delivery cannot make the same event take effect twice.
 *
 * Every method takes the consumer's name (by convention its Kafka groupId). Each consumer has its own rows: one
 * consumer recording an event never makes another consumer skip it.
 *
 * An interface so the handler can be unit-tested with an in-memory double; the real implementation is
 * {@link R2dbcDomainEventStore}.
 */
public interface DomainEventStore {

    /**
     * @return true if the event was new for this consumer (and is now recorded), false if this consumer had recorded
     *         its id before. Call it in the same transaction as the consumer's side effect.
     */
    Mono<Boolean> addIfAbsent(String consumer, DomainEvent event);

    /** @return the events this consumer recorded most recently, newest first, at most {@code limit}. */
    Flux<DomainEvent> findRecent(String consumer, int limit);
}
