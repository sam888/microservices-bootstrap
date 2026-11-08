-- One account per seeded member. Re-runnable: a member that already has an account is skipped.
-- ORDER BY keeps the ids stable (account 1 = Alice 1000.00, 2 = Bob 500.00, 3 = Carol 250.00), see 02_seed.sql.
INSERT INTO account (member_id, balance)
SELECT m.id, v.balance
FROM (VALUES (1, 'alice@example.com', 1000.00),
             (2, 'bob@example.com',    500.00),
             (3, 'carol@example.com',  250.00)) AS v(ord, email, balance)
JOIN member m ON lower(m.email) = v.email
WHERE NOT EXISTS (SELECT 1 FROM account a WHERE a.member_id = m.id)
ORDER BY v.ord;
