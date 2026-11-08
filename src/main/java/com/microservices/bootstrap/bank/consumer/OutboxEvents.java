package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Shared helpers for the consumers of outbox topics: reading the headers Debezium's outbox router sets ("id",
 * "eventType") and turning a record into a {@link DomainEvent}. Every outbox consumer validates events the same way.
 */
final class OutboxEvents {

    static final String ID_HEADER = "id";
    static final String EVENT_TYPE_HEADER = "eventType";

    private OutboxEvents() {
    }

    /** @return the header's value as text, or null if the record has no such header. */
    static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    /**
     * @return the event, or null for a tombstone (a record without a value), which consumers skip.
     * @throws IllegalArgumentException for an event without an id or with a malformed payload. BankKafkaErrorConfig
     *         treats it as non-retryable, so the record goes straight to the dead-letter topic.
     */
    static DomainEvent parse(ObjectMapper mapper, String eventId, String eventType, String topic, String payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Outbox event without id header");
        }
        JsonNode payload;
        try {
            payload = mapper.readTree(payloadJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed outbox payload", e);
        }
        return new DomainEvent(eventId, eventType, topic, payload, Instant.now());
    }
}
