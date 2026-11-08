#!/usr/bin/env bash
# Applies every db/*.sql file, in order, to the RUNNING Postgres container, then prints a row-count summary.
#
# Why this exists: Postgres runs /docker-entrypoint-initdb.d (our db/ folder) only ONCE, when its data volume is
# empty. If the volume was created by an older version of these scripts, tables and functions added later (account, inbox, transfer_funds, ...)
# never appear, and `docker compose up` will not fix that. This script brings an existing database up to date
# without wiping it. All scripts are re-runnable, so running it on an up-to-date database changes nothing.
#
#   ./scripts/apply-db.sh
#   POSTGRES_CONTAINER=bank-postgres POSTGRES_DB=bankdb ./scripts/apply-db.sh    # defaults shown
set -euo pipefail
cd "$(dirname "$0")/.."

container="${POSTGRES_CONTAINER:-bank-postgres}"
db="${POSTGRES_DB:-bankdb}"
# client_min_messages=warning hides the harmless "already exists, skipping" notices of re-runs
psql_in() { docker exec -i -e PGOPTIONS="-c client_min_messages=warning" "$container" psql -v ON_ERROR_STOP=1 -q -U postgres -d "$db" "$@"; }

for f in db/*.sql; do
  echo "applying $f"
  psql_in < "$f"
done

echo
echo "Row counts (inbox and outbox are empty until the app runs: see db/07_inbox.sql):"
psql_in -c "
  SELECT 'member_status' AS table_name, count(*) AS rows FROM member_status
  UNION ALL SELECT 'member',  count(*) FROM member
  UNION ALL SELECT 'account', count(*) FROM account
  UNION ALL SELECT 'outbox',  count(*) FROM outbox
  UNION ALL SELECT 'inbox',   count(*) FROM inbox;"

echo
echo "Functions the application calls (db/08..10): all 7 should be listed."
psql_in -c "
  SELECT p.proname AS function_name, pg_get_function_identity_arguments(p.oid) AS arguments
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.prokind = 'f' AND p.proname <> 'set_updated_at'
  ORDER BY p.proname;"
