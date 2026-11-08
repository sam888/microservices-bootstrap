package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * Postgres (R2DBC) implementation of the inbox. The SQL lives in the functions inbox_record_event and
 * inbox_find_recent (cdc-platform/db/10_functions_inbox.sql); this class only calls them.
 *
 * Deduplication is decided inside inbox_record_event by ONE statement, INSERT ... ON CONFLICT DO NOTHING, not by
 * "SELECT to check, then INSERT": two deliveries of the same event handled at the same moment could both pass a
 * SELECT. With the primary key (consumer, event_id), the database decides: for one consumer, exactly one concurrent
 * call returns true, every other false. Another consumer gets its own true for the same event.
 *
 * In a consumer that does real work per event, run the inbox call and that work in ONE database transaction, so the
 * "processed" marker and the side effect commit or roll back together (see LedgerProcessor). The calls here join a
 * transaction the caller opened, because DatabaseClient takes part in reactive transactions.
 */
@Component
public class R2dbcDomainEventStore implements DomainEventStore {

    private final DatabaseClient db;
    private final ObjectMapper mapper;

    public R2dbcDomainEventStore(DatabaseClient db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    @Override
    public Mono<Boolean> addIfAbsent(String consumer, DomainEvent event) {
        final String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalArgumentException("Cannot serialize event payload", e));
        }

        DatabaseClient.GenericExecuteSpec spec = db
                .sql("SELECT inbox_record_event(:consumer, :eventId, :eventType, :topic, :payload) AS is_new")
                .bind("consumer", consumer)
                .bind("eventId", event.eventId())
                .bind("topic", event.topic())
                .bind("payload", payloadJson);
        // event_type is nullable (a producer may omit the "eventType" header); R2DBC cannot bind a plain null
        spec = event.eventType() == null
                ? spec.bindNull("eventType", String.class)
                : spec.bind("eventType", event.eventType());

        return spec.map((row, meta) -> row.get("is_new", Boolean.class)).one();
    }

    @Override
    public Flux<DomainEvent> findRecent(String consumer, int limit) {
        return db.sql("SELECT * FROM inbox_find_recent(:consumer, :limit)")
                .bind("consumer", consumer)
                .bind("limit", limit)
                .map((row, meta) -> new DomainEvent(
                        row.get("event_id", String.class),
                        row.get("event_type", String.class),
                        row.get("topic", String.class),
                        parse(row.get("payload", String.class)),
                        row.get("received_at", OffsetDateTime.class).toInstant()))
                .all();
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt payload in inbox", e);
        }
    }
}
