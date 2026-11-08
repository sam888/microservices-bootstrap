-- Stored functions, part 3: the consumer-side inbox (table created in 07_inbox.sql).

-- Records that a consumer has processed an event. Returns TRUE if this (consumer, event id) pair was new, FALSE if
-- it had been recorded before (a redelivery, which the caller must skip). Each consumer (Kafka groupId) has its own
-- rows, so one consumer recording an event never makes another consumer skip it.
--
-- ONE statement decides: INSERT ... ON CONFLICT DO NOTHING. Two deliveries of the same event to the same consumer,
-- handled at the same moment, cannot both win, which a "SELECT to check, then INSERT" could not guarantee.
-- The payload arrives as text (the application sends it as-is) and is stored as JSONB.
--
-- Call it in the SAME transaction as the consumer's side effect (see LedgerProcessor), so the "processed" marker and
-- the effect commit or roll back together.
--
-- DROP first: the parameter lists changed when the consumer column was added. CREATE OR REPLACE with new parameters
-- would add a second function (an overload) and leave the old one behind, so the old signatures are removed here.
DROP FUNCTION IF EXISTS inbox_record_event(VARCHAR, VARCHAR, VARCHAR, TEXT);
DROP FUNCTION IF EXISTS inbox_find_recent(INTEGER);

CREATE OR REPLACE FUNCTION inbox_record_event(
    p_consumer   VARCHAR,   -- the consumer's groupId, e.g. 'ledger-posting'
    p_event_id   VARCHAR,
    p_event_type VARCHAR,   -- may be NULL (a producer may omit the "eventType" header)
    p_topic      VARCHAR,
    p_payload    TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO inbox (consumer, event_id, event_type, topic, payload)
    VALUES (p_consumer, p_event_id, p_event_type, p_topic, p_payload::jsonb)
    ON CONFLICT (consumer, event_id) DO NOTHING;

    RETURN FOUND;   -- true only if a row was actually inserted
END;
$$;

-- The events one consumer has recorded most recently, newest first (GET /demo/bank/events shows
-- bootstrap-domain-events).
CREATE OR REPLACE FUNCTION inbox_find_recent(p_consumer VARCHAR, p_limit INTEGER)
RETURNS TABLE (event_id VARCHAR, event_type VARCHAR, topic VARCHAR, payload TEXT, received_at TIMESTAMPTZ)
LANGUAGE sql
STABLE
AS $$
    SELECT i.event_id, i.event_type, i.topic, i.payload::text, i.received_at
    FROM inbox i
    WHERE i.consumer = p_consumer
    ORDER BY i.received_at DESC, i.event_id
    LIMIT p_limit;
$$;
