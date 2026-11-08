package com.microservices.bootstrap.bank.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes raw row changes of the member table (Part A of the pattern: plain CDC, no outbox). */
@Component
public class MemberChangeListener {

    /** This consumer's name: its listener id and its Kafka groupId. */
    public static final String CONSUMER = "bootstrap-member-cache";

    private final MemberCacheInvalidator invalidator;

    public MemberChangeListener(MemberCacheInvalidator invalidator) {
        this.invalidator = invalidator;
    }

    // id: the handle ListenerAdminService uses to stop/start just this consumer (same value as the group)
    @KafkaListener(id = CONSUMER, topics = "${app.bank-cdc.topics.member}", groupId = CONSUMER)
    public void onMessage(ConsumerRecord<String, String> record) {
        invalidator.handle(record.value());
    }
}
