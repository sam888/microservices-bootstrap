-- Stored functions, part 1: the outbox writer.
--
-- Why FUNCTIONS and not PROCEDURES: in Postgres, CREATE PROCEDURE (invoked with CALL) exists mainly for transaction
-- control and cannot be used inside a SELECT or return a result set. CREATE FUNCTION can return rows, can be called
-- as `SELECT * FROM fn(...)`, and runs inside the caller's transaction, which is exactly what this project needs.
-- "Stored procedure" in everyday talk usually means either; every routine in 08..10 is a function.
--
-- Re-runnable: CREATE OR REPLACE. (If you ever change a function's RETURN TYPE or OUT column names, Postgres
-- refuses to replace it: DROP FUNCTION name(arg types) first.)

-- Writes ONE row to the outbox table and returns its id (the event id that Debezium puts in the Kafka header "id").
--
-- This is an internal building block: only the business functions (onboard_member, transfer_funds) call it, from
-- inside their own transaction. The application never calls it directly. That is how this design keeps the rule
-- "an outbox row exists if and only if the business change committed", which the Java version used to enforce
-- with @Transactional(propagation = MANDATORY).
CREATE OR REPLACE FUNCTION outbox_publish(
    p_aggregate_type VARCHAR,   -- decides the topic: outbox.event.<aggregate_type>
    p_aggregate_id   VARCHAR,   -- becomes the Kafka message key (per-key ordering)
    p_type           VARCHAR,   -- event type, e.g. FundsTransferred (Kafka header "eventType")
    p_payload        JSONB      -- becomes the Kafka message value
) RETURNS UUID
LANGUAGE sql
AS $$
    INSERT INTO outbox (aggregate_type, aggregate_id, type, payload)
    VALUES (p_aggregate_type, p_aggregate_id, p_type, p_payload)
    RETURNING id;
$$;
