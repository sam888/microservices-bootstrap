-- Transactional outbox: written in the SAME transaction as the business change.
-- Debezium watches this table (not member/account) and routes each row to Kafka.
CREATE TABLE IF NOT EXISTS outbox (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),  -- becomes the event id (header "id")
    aggregate_type  VARCHAR(50)  NOT NULL,   -- decides the topic: outbox.event.<aggregate_type>
    aggregate_id    VARCHAR(50)  NOT NULL,   -- becomes the Kafka message key (per-key ordering)
    type            VARCHAR(100) NOT NULL,   -- event type, e.g. FundsTransferred (header "eventType")
    payload         JSONB        NOT NULL,   -- becomes the message value
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'outbox_pub') THEN
        CREATE PUBLICATION outbox_pub FOR TABLE outbox;
    END IF;
END $$;
