package com.microservices.bootstrap.bank.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point of ONE consumer, the event log (group "bootstrap-domain-events"). It subscribes to every outbox
 * topic because its job is to record every business event; that is the right scope for this consumer.
 *
 * It is NOT the place for other side effects. Each side effect (posting to a ledger, sending a notification, ...)
 * gets its own listener with its own groupId, so Kafka fans every event out to each of them and they fail, retry and
 * scale independently. See {@link LedgerPostingListener}.
 *
 * Runs on Kafka listener threads, not on the Netty event loop, so blocking work here does not hurt WebFlux.
 */
@Component
public class OutboxEventListener {

    private final DomainEventHandler handler;

    public OutboxEventListener(DomainEventHandler handler) {
        this.handler = handler;
    }

    // id: the handle ListenerAdminService uses to stop/start just this consumer (same value as the group)
    @KafkaListener(
            id = DomainEventHandler.CONSUMER,
            topics = {"${app.bank-cdc.topics.outbox-member}", "${app.bank-cdc.topics.outbox-account}"},
            groupId = DomainEventHandler.CONSUMER)
    public void onMessage(ConsumerRecord<String, String> record) {
        handler.handle(
                OutboxEvents.header(record, OutboxEvents.ID_HEADER),
                OutboxEvents.header(record, OutboxEvents.EVENT_TYPE_HEADER),
                record.topic(),
                record.value());
    }
}
