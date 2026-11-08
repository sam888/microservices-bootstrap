package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * The "ledger-posting" consumer's work: post each FundsTransferred event to the ledger AT MOST ONCE, however often
 * Kafka delivers it.
 *
 * The inbox record and the side effect run in ONE database transaction (TransactionalOperator):
 *
 *   BEGIN;
 *     inbox_record_event('ledger-posting', id, ...)   -- FALSE: already handled, skip the side effect
 *     post to the ledger                               -- only for a new event
 *   COMMIT;                                            -- both or neither; only then does Kafka commit the offset
 *
 * A crash before COMMIT rolls both back, and Kafka redelivers the event. A crash after COMMIT but before the offset
 * commit also redelivers it, but then the inbox says "already handled" and nothing is posted twice.
 *
 * The ledger itself is simulated with a log line. A real implementation would write its ledger rows with the same
 * DatabaseClient (or call a stored function) inside this transaction. A side effect outside the database (an email, a
 * REST call) cannot join the transaction: pass the event id to it as an idempotency key instead.
 */
@Slf4j
@Component
public class LedgerProcessor {

    /** This consumer's name: its Kafka groupId and its key in the inbox table. */
    public static final String CONSUMER = "ledger-posting";

    private final ObjectMapper mapper;
    private final DomainEventStore inbox;
    private final TransactionalOperator tx;   // auto-configured by Spring Boot (R2DBC transaction manager)

    public LedgerProcessor(ObjectMapper mapper, DomainEventStore inbox, TransactionalOperator tx) {
        this.mapper = mapper;
        this.inbox = inbox;
        this.tx = tx;
    }

    /**
     * @return true if the transfer was posted, false if skipped as a duplicate or tombstone.
     * Invalid input throws IllegalArgumentException (non-retryable: straight to the dead-letter topic).
     */
    public boolean process(String eventId, String eventType, String topic, String payloadJson) {
        DomainEvent event = OutboxEvents.parse(mapper, eventId, eventType, topic, payloadJson);
        if (event == null) {
            return false; // tombstone
        }

        Mono<Boolean> work = inbox.addIfAbsent(CONSUMER, event)
                .flatMap(isNew -> isNew
                        ? postToLedger(event).thenReturn(true)    // the side effect, only for a new event
                        : Mono.just(false));                      // already handled: skip

        // Nothing above has run yet: "work" is only a plan. block() starts it and waits for it to finish:
        //   BEGIN; inbox_record_event('ledger-posting', ...); postToLedger(...) only if new; COMMIT;
        // The two writes (inbox row, ledger posting) commit together or not at all.
        // Waiting is fine, and needed: this is a Kafka listener thread, not a Netty event-loop thread, and Kafka
        // commits the offset when onMessage returns, which must be after the database commit. (Between records the
        // thread waits for new messages in the listener container's poll loop, not here.)
        boolean isNew = Boolean.TRUE.equals(tx.transactional(work).block());
        if (!isNew) {
            log.info("Ledger: skipped duplicate {} id={}", eventType, eventId);
        }
        return isNew;
    }

    /** The side effect, simulated. Part of the reactive chain, so it runs inside the transaction. */
    private Mono<Void> postToLedger(DomainEvent event) {
        System.out.println("Ledger....");
        System.out.println("Ledger....");
        System.out.println("Ledger....");
        JsonNode p = event.payload();
        return Mono.fromRunnable(() -> log.info("Ledger: posted transfer id={} from={} to={} amount={} {}",
                event.eventId(),
                p.path("fromAccountId").asText(),
                p.path("toAccountId").asText(),
                p.path("amount").asText(),
                p.path("currency").asText()));
    }
}
