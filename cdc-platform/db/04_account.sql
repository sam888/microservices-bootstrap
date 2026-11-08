CREATE SEQUENCE IF NOT EXISTS account_number_seq START 1000;

CREATE TABLE IF NOT EXISTS account (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member (id),
    account_number  VARCHAR(20)   NOT NULL UNIQUE DEFAULT ('ACC-' || nextval('account_number_seq')),
    balance         NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),  -- last line of defence vs overdraft
    currency        CHAR(3)       NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_account_member ON account (member_id);

DROP TRIGGER IF EXISTS trg_account_updated_at ON account;
CREATE TRIGGER trg_account_updated_at BEFORE UPDATE ON account
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
