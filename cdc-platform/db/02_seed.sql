-- Seed data. Re-runnable: rows that already exist are left alone (and no identity values are burned).
-- The explicit ORDER BY matters: without it Postgres may insert the rows in any order, and the ids (Alice = 1,
-- Bob = 2, Carol = 3) that the README and the API examples rely on would not be guaranteed.
INSERT INTO member_status (id, code, description) VALUES
    (1, 'ACTIVE',    'Member in good standing'),
    (2, 'SUSPENDED', 'Temporarily blocked'),
    (3, 'CLOSED',    'Account closed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO member (first_name, last_name, email)
SELECT v.first_name, v.last_name, v.email
FROM (VALUES (1, 'Alice', 'Chan', 'alice@example.com'),
             (2, 'Bob',   'Lee',  'bob@example.com'),
             (3, 'Carol', 'Wong', 'carol@example.com')) AS v(ord, first_name, last_name, email)
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE lower(m.email) = lower(v.email))
ORDER BY v.ord;
