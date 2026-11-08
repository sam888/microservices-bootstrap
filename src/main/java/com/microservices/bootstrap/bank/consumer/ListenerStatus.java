package com.microservices.bootstrap.bank.consumer;

/**
 * State of one Kafka listener in THIS app instance. running = false means this instance has no member in the group;
 * the group is Empty (and its offsets can be reset) only when no other instance runs that listener either.
 */
public record ListenerStatus(String id, String groupId, boolean running) {
}
