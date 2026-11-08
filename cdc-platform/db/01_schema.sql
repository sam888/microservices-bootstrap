-- Lookup table
-- Every script in this folder is re-runnable (scripts/apply-db.sh relies on that).
CREATE TABLE IF NOT EXISTS member_status (
    id           SMALLINT     PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    description  VARCHAR(100) NOT NULL
);

-- CDC source: Debezium watches this table
CREATE TABLE IF NOT EXISTS member (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    address           VARCHAR(255) NULL,
    member_status_id  SMALLINT     NOT NULL DEFAULT 1 REFERENCES member_status (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_member_status ON member (member_status_id);
-- Case-insensitive uniqueness: Alice@Example.com and alice@example.com are the same person.
CREATE UNIQUE INDEX IF NOT EXISTS uq_member_email_lower ON member (lower(email));

-- Keep updated_at honest, even for edits made directly in psql
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_member_updated_at ON member;
CREATE TRIGGER trg_member_updated_at BEFORE UPDATE ON member
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
