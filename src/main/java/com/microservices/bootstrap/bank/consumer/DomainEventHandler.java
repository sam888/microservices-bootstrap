package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The "event log" consumer: records every outbox business event in the inbox, which GET /demo/bank/events shows.
 * Recording the event IS its whole side effect, so the inbox call alone is the unit of work. A consumer with a real
 * side effect runs the inbox call and that effect in one transaction: see {@link LedgerProcessor}.
 *
 * Kept free of Kafka types so it is unit-testable.
 */
@Slf4j
@Component
public class DomainEventHandler {

    /** This consumer's name: its Kafka groupId and its key in the inbox table. */
    public static final String CONSUMER = "bootstrap-domain-events";

    private final ObjectMapper mapper;
    private final DomainEventStore store;

    public DomainEventHandler(ObjectMapper mapper, DomainEventStore store) {
        this.mapper = mapper;
        this.store = store;
    }

    /**
     * @return true if processed, false if skipped as a duplicate or tombstone.
     * Invalid input throws IllegalArgumentException, which BankKafkaErrorConfig treats as non-retryable
     * (straight to the dead-letter topic).
     */
    public boolean handle(String eventId, String eventType, String topic, String payloadJson) {
        DomainEvent event = OutboxEvents.parse(mapper, eventId, eventType, topic, payloadJson);
        if (event == null) {
            return false; // tombstone
        }

        // Safe to block: this runs on a Kafka listener thread, not on a Netty event-loop thread.
        boolean isNew = Boolean.TRUE.equals(store.addIfAbsent(CONSUMER, event).block());
        if (isNew) {
            log.info("Processed {} id={} topic={}", eventType, eventId, topic);
        } else {
            log.info("Skipped duplicate {} id={}", eventType, eventId);
        }
        return isNew;
    }
}
