-- Stored functions, part 2: the business operations of the demo API, plus two reads.
--
-- Each write function is ONE database statement from the application's point of view, so it is atomic by itself:
-- every row it changes, including the outbox row, commits together or not at all. If the caller already opened a
-- transaction, the function simply joins it.
--
-- Errors the application must tell apart carry custom SQLSTATE codes. The "UB" prefix is this project's own
-- convention (not a Postgres standard): any 5 characters of 0-9A-Z are allowed, classes 0-4 and A-H belong to the
-- SQL standard, and P0 / XX are used by Postgres itself. Every caller of these functions shares this list; a new
-- code also needs a new case in ProcedureErrors (Java), otherwise it surfaces as an unmapped error (HTTP 500).
--     UB001  not found            -> HTTP 404
--     UB002  insufficient funds   -> HTTP 422
--     UB003  invalid request      -> HTTP 400
-- Anything else (unique violation 23505, a failed CHECK constraint 23514, ...) keeps Postgres' own code.
--
-- Event payloads (the public contract of outbox.event.member / outbox.event.account) are built here, with
-- jsonb_build_object. They carry ids and business facts only: no email, no address.

-- ---------------------------------------------------------------------------------------------------------------
-- onboard_member: member + account + MemberOnboarded event, one transaction
-- Naming in the plpgsql functions: p_ parameters, v_ local variables, out_ output columns. In plpgsql the
-- RETURNS TABLE columns are also variables inside the body, so an output column named like a table column
-- ("id", "balance", ...) would make statements such as RETURNING id ambiguous; the prefixes rule that out.
-- DROP first: CREATE OR REPLACE cannot change a function's output columns, so re-running this script over an older
-- version (with other output names) would otherwise fail. Nothing else depends on these functions.
-- ---------------------------------------------------------------------------------------------------------------
DROP FUNCTION IF EXISTS onboard_member(VARCHAR, VARCHAR, VARCHAR, NUMERIC);
CREATE OR REPLACE FUNCTION onboard_member(
    p_first_name      VARCHAR,
    p_last_name       VARCHAR,
    p_email           VARCHAR,
    p_initial_deposit NUMERIC
) RETURNS TABLE (out_member_id BIGINT, out_account_id BIGINT, out_account_number VARCHAR)
LANGUAGE plpgsql
AS $$
DECLARE
    v_member_id      BIGINT;
    v_account_id     BIGINT;
    v_account_number VARCHAR;
    v_currency       CHAR(3);
BEGIN
    IF p_initial_deposit IS NULL OR p_initial_deposit < 0 THEN
        RAISE EXCEPTION 'Initial deposit must be zero or more' USING ERRCODE = 'UB003';
    END IF;

    INSERT INTO member (first_name, last_name, email)
    VALUES (p_first_name, p_last_name, p_email)       -- a duplicate email raises unique_violation (23505)
    RETURNING id INTO v_member_id;

    INSERT INTO account (member_id, balance)
    VALUES (v_member_id, p_initial_deposit)
    RETURNING id, account_number, currency INTO v_account_id, v_account_number, v_currency;

    PERFORM outbox_publish('member', v_member_id::text, 'MemberOnboarded', jsonb_build_object(
        'memberId',       v_member_id,
        'accountId',      v_account_id,
        'accountNumber',  v_account_number,
        'initialDeposit', p_initial_deposit,
        'currency',       v_currency,
        'occurredAt',     now()));

    RETURN QUERY SELECT v_member_id, v_account_id, v_account_number;
END;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- transfer_funds: debit + credit + FundsTransferred event, one transaction
-- (same naming and the same DROP-first reason as onboard_member above)
-- ---------------------------------------------------------------------------------------------------------------
DROP FUNCTION IF EXISTS transfer_funds(BIGINT, BIGINT, NUMERIC);
CREATE OR REPLACE FUNCTION transfer_funds(
    p_from_account_id BIGINT,
    p_to_account_id   BIGINT,
    p_amount          NUMERIC
) RETURNS TABLE (out_transfer_id UUID, out_from_balance NUMERIC, out_to_balance NUMERIC)
LANGUAGE plpgsql
AS $$
DECLARE
    v_row           RECORD;
    v_from_found    BOOLEAN := FALSE;
    v_to_found      BOOLEAN := FALSE;
    v_from_balance  NUMERIC;
    v_to_balance    NUMERIC;
    v_from_currency CHAR(3);
    v_to_currency   CHAR(3);
    v_transfer_id   UUID := gen_random_uuid();
BEGIN
    IF p_from_account_id IS NULL OR p_to_account_id IS NULL THEN
        RAISE EXCEPTION 'Both accounts are required' USING ERRCODE = 'UB003';
    END IF;
    IF p_amount IS NULL OR p_amount <= 0 OR p_amount <> round(p_amount, 2) THEN
        RAISE EXCEPTION 'Amount must be greater than zero, with at most 2 decimal places' USING ERRCODE = 'UB003';
    END IF;
    IF p_from_account_id = p_to_account_id THEN
        RAISE EXCEPTION 'Cannot transfer to the same account' USING ERRCODE = 'UB003';
    END IF;

    -- Lock both rows in id order: concurrent A->B and B->A transfers then queue up instead of deadlocking.
    FOR v_row IN
        SELECT a.id, a.balance, a.currency
        FROM account a
        WHERE a.id IN (p_from_account_id, p_to_account_id)
        ORDER BY a.id
        FOR UPDATE
    LOOP
        IF v_row.id = p_from_account_id THEN
            v_from_found := TRUE; v_from_balance := v_row.balance; v_from_currency := v_row.currency;
        ELSE
            v_to_found := TRUE;   v_to_balance := v_row.balance;   v_to_currency := v_row.currency;
        END IF;
    END LOOP;

    IF NOT v_from_found THEN
        RAISE EXCEPTION 'Account % not found', p_from_account_id USING ERRCODE = 'UB001';
    END IF;
    IF NOT v_to_found THEN
        RAISE EXCEPTION 'Account % not found', p_to_account_id USING ERRCODE = 'UB001';
    END IF;
    IF v_from_currency <> v_to_currency THEN
        RAISE EXCEPTION 'Cross-currency transfers are not supported' USING ERRCODE = 'UB003';
    END IF;
    IF v_from_balance < p_amount THEN
        RAISE EXCEPTION 'Insufficient funds in account %', p_from_account_id USING ERRCODE = 'UB002';
    END IF;

    UPDATE account SET balance = balance - p_amount WHERE id = p_from_account_id;
    UPDATE account SET balance = balance + p_amount WHERE id = p_to_account_id;

    PERFORM outbox_publish('account', p_from_account_id::text, 'FundsTransferred', jsonb_build_object(
        'transferId',    v_transfer_id,
        'fromAccountId', p_from_account_id,
        'toAccountId',   p_to_account_id,
        'amount',        p_amount,
        'currency',      v_from_currency,
        'occurredAt',    now()));

    RETURN QUERY SELECT v_transfer_id, v_from_balance - p_amount, v_to_balance + p_amount;
END;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Reads. Zero rows means "not found"; the Java service turns that into a 404.
-- ---------------------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_account(p_account_id BIGINT)
RETURNS TABLE (id BIGINT, member_id BIGINT, account_number VARCHAR, balance NUMERIC, currency CHAR(3))
LANGUAGE sql
STABLE
AS $$
    SELECT a.id, a.member_id, a.account_number, a.balance, a.currency
    FROM account a
    WHERE a.id = p_account_id;
$$;

CREATE OR REPLACE FUNCTION get_member_profile(p_member_id BIGINT)
RETURNS TABLE (id BIGINT, first_name VARCHAR, last_name VARCHAR, email VARCHAR, address VARCHAR,
               member_status_id SMALLINT)
LANGUAGE sql
STABLE
AS $$
    SELECT m.id, m.first_name, m.last_name, m.email, m.address, m.member_status_id
    FROM member m
    WHERE m.id = p_member_id;
$$;
