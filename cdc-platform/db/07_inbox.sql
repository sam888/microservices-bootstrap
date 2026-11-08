-- Inbox: the consumer-side mirror of the outbox (05_outbox.sql).
-- One row per (consumer, event id) that has already been processed. The PRIMARY KEY is what makes Kafka's
-- at-least-once delivery harmless: a redelivered event hits ON CONFLICT (consumer, event_id) DO NOTHING and is skipped.
--
-- Why the key includes the consumer: every consumer group gets its own copy of each event (Kafka fan-out), and each
-- one must decide for ITSELF whether it has handled that event. Keyed by event_id alone, the first consumer to record
-- an event would make every other consumer see it as a duplicate and skip it. By convention "consumer" is the Kafka
-- groupId: bootstrap-domain-events (the event log behind GET /demo/bank/events) and ledger-posting.
--
-- In a real deployment this table lives in each CONSUMER service's own database, and the row is inserted in the same
-- transaction as the side effect it protects. Here it shares the demo database for convenience.
--
-- Not captured by Debezium: both publications (03_cdc.sql, 05_outbox.sql) list their tables explicitly.
-- NOTE: there is deliberately NO seed for this table (and none for outbox). Both are written by the running
-- application: the API writes outbox rows, the consumers write inbox rows. A seeded row would be a fake "already
-- processed" event, which is exactly what the table must never contain.
CREATE TABLE IF NOT EXISTS inbox (
    consumer     VARCHAR(100) NOT NULL,                   -- who processed it: the consumer's groupId
    event_id     VARCHAR(100) NOT NULL,                   -- the outbox row's UUID, taken from Kafka header "id"
    event_type   VARCHAR(100),                            -- e.g. FundsTransferred (header "eventType")
    topic        VARCHAR(255) NOT NULL,                   -- topic the event arrived on
    payload      JSONB        NOT NULL,                   -- the event body, kept for inspection and debugging
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

-- Upgrade an inbox created by an earlier version of this script (PRIMARY KEY (event_id), no consumer column).
-- Every existing row was written by the only consumer of that version, bootstrap-domain-events. Runs once; on an
-- up-to-date table it does nothing, so the script stays re-runnable (./scripts/apply-db.sh).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'inbox' AND column_name = 'consumer') THEN
        ALTER TABLE inbox ADD COLUMN consumer VARCHAR(100) NOT NULL DEFAULT 'bootstrap-domain-events';
        ALTER TABLE inbox ALTER COLUMN consumer DROP DEFAULT;     -- new rows must always name their consumer
        ALTER TABLE inbox DROP CONSTRAINT inbox_pkey;
        ALTER TABLE inbox ADD PRIMARY KEY (consumer, event_id);
    END IF;
END $$;

-- Supports "newest first" reads and a periodic purge of rows older than the topic's retention.
CREATE INDEX IF NOT EXISTS idx_inbox_received_at ON inbox (received_at);
