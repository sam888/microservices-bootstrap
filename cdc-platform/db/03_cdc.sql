-- Publication Debezium's pgoutput plugin streams from. Explicit = least surprise,
-- and Debezium then doesn't need table-owner rights to create it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'member_pub') THEN
        CREATE PUBLICATION member_pub FOR TABLE member;
    END IF;
END $$;

-- Emit the full old row in "before" on UPDATE/DELETE (default only carries the PK).
ALTER TABLE member REPLICA IDENTITY FULL;
