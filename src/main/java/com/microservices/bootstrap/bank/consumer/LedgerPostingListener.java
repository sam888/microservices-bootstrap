package com.microservices.bootstrap.bank.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point of the "ledger-posting" consumer: one consumer group for ONE side effect.
 *
 * It has its own groupId, so Kafka delivers every event on outbox.event.account to it independently of the event-log
 * consumer (OutboxEventListener): its own offsets, lag and retries. If posting fails, only this consumer stalls and
 * dead-letters; the others carry on. Adding another side effect means adding another listener like this one, with
 * another groupId; no existing class changes.
 *
 * A new group starts from the beginning of the topic (auto-offset-reset: earliest), so on its first start it also
 * posts every transfer already in the topic.
 */
@Component
public class LedgerPostingListener {

    private static final String FUNDS_TRANSFERRED = "FundsTransferred";

    private final LedgerProcessor processor;

    public LedgerPostingListener(LedgerProcessor processor) {
        this.processor = processor;
    }

    // id: the handle ListenerAdminService uses to stop/start just this consumer (same value as the group)
    @KafkaListener(id = LedgerProcessor.CONSUMER, topics = "${app.bank-cdc.topics.outbox-account}",
                   groupId = LedgerProcessor.CONSUMER)
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = OutboxEvents.header(record, OutboxEvents.EVENT_TYPE_HEADER);
        if (!FUNDS_TRANSFERRED.equals(eventType)) {
            return;   // not an event this consumer acts on; returning normally lets Kafka commit the offset
        }
        processor.process(
                OutboxEvents.header(record, OutboxEvents.ID_HEADER), eventType, record.topic(), record.value());
    }
}
