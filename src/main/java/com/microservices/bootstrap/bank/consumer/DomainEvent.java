package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** A business event received from an outbox topic. eventId is the outbox row's UUID (Kafka header "id"). */
public record DomainEvent(String eventId, String eventType, String topic, JsonNode payload, Instant receivedAt) {
}
