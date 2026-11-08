# Change Data Capture (CDC) & Transactional Outbox with Debezium

This branch contains the proof-of-concept implementation behind the tutorial
**[CDC & Transactional Outbox with Debezium](https://github.com/sam888/cdc-transactional-outbox-debezium-kafka)**:
a Spring Boot (WebFlux, R2DBC) service that publishes business events reliably through a transactional outbox,
with Postgres, Debezium on Kafka Connect, Kafka and Redis.

## What's in this branch

- `src/main/java/.../bank/producer`: REST API. Each operation is one Postgres function that writes the business rows
  **and** the outbox row in one transaction.
- `src/main/java/.../bank/consumer`: Three Kafka consumers, one consumer group per side effect. Each consumer de-duplicates through an inbox table:
  * Redis cache eviction (raw CDC) consumer
  * An audit-trail event log consumer
  * A ledger consumer
- `cdc-platform/`: Docker Compose platform (Postgres, Kafka, Kafka Connect + Debezium, Redis), SQL scripts and connector definitions.

## Run it

Run each step from the repository root, and check its result before moving on.

**1. Start the platform** (Postgres, pgAdmin, Kafka, Kafka Connect with Debezium, Conduktor Console, Conduktor Postgress, Redis and  Redis Commander):

```bash
cd cdc-platform
docker compose --profile ui up -d
```

Wait until `docker compose ps` shows `postgres`, `kafka` and `redis` as **healthy** and `connect` as **running**.
Kafka Connect needs another 20 to 60 seconds after that before it answers.

**2. Register the Debezium connectors** (still in `cdc-platform/`):

```bash
./scripts/register-connector.sh
```

Expected: `created: member-connector`, `created: outbox-connector`, and `"state":"RUNNING"` for each.
A `Connection refused` means Kafka Connect isn't ready yet: wait a little and run it again.

**3. Start the Spring Boot app** (back in the repository root):

```bash
cd ..
./gradlew bootRun
```

The app listens on <http://localhost:9090>.

See the **[tutorial](https://github.com/sam888/cdc-transactional-outbox-debezium-kafka)** for the full walkthrough:
architecture, Postman calls, Conduktor, replay, and the design behind every piece.

---

Samuel Huang
