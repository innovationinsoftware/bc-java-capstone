# 06 — Kafka Events

Kafka is **already deployed** for you by the instructor. The broker URL is provided. You will create no topic by hand — the topic is pre-created. Your job is to publish correctly.

## Topic

| | Value |
|---|---|
| Topic name | `transactions.completed` |
| Partitions | (instructor-defined) |
| Replication | (instructor-defined) |
| Cleanup policy | `delete` |
| Key serializer | `StringSerializer` |
| Value serializer | `JsonSerializer` (Spring Kafka) |

The exact bootstrap-servers URL is set in `application.yml` via `${KAFKA_BOOTSTRAP_SERVERS}` (env var, instructor-supplied).

## Event payload

One Java record, one JSON envelope:

```java
public record TransactionEvent(
    String eventId,             // UUID, generated at publish time, idempotency key
    String transactionId,       // FK to TRANSACTIONS.TRANSACTION_ID
    String accountId,
    String ownerId,
    String type,                // DEPOSIT | WITHDRAWAL | TRANSFER_OUT | TRANSFER_IN
    BigDecimal amount,
    String currency,
    String status,              // COMPLETED | FAILED — only these are emitted
    String counterparty,        // nullable
    String transferGroupId,     // nullable
    Instant occurredAt          // when the DB transaction committed
) {}
```

Serialised:

```json
{
  "eventId": "evt_8c2d...",
  "transactionId": "txn_a1b2...",
  "accountId": "acc_001",
  "ownerId": "usr_demo",
  "type": "WITHDRAWAL",
  "amount": 50.00,
  "currency": "USD",
  "status": "COMPLETED",
  "counterparty": null,
  "transferGroupId": null,
  "occurredAt": "2026-04-26T13:45:30.123Z"
}
```

### Why these fields

- `eventId` is the **idempotency key**. A downstream consumer reading at-least-once will see duplicates; the `eventId` lets it dedupe. The rubric specifically mentions idempotency under "Exceeds — handles producer failures, considers idempotency."
- `ownerId` is denormalised onto the event so consumers don't need to join back to the accounts service.
- `status` distinguishes `COMPLETED` from `FAILED`. Both are emitted — fraud monitoring cares about failed attempts too.

### Key

Use the **`accountId`** as the Kafka message key. This guarantees that all events for the same account land on the same partition and are consumed in commit order. Do **not** use `transactionId` as the key — that fragments the partition and breaks downstream "account history" consumers.

## Producer configuration

In `application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 10
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 10
        compression.type: lz4
```

This is exactly the configuration the Module 8 slides walked through. `acks=all` plus `enable.idempotence=true` gives you no-duplicate, durable writes inside the broker. (The application-level `eventId` is for the consumer's protection, not the broker's.)

## Publisher

```java
@Service
public class TransactionEventPublisher {
    private final KafkaTemplate<String, TransactionEvent> kafka;

    public TransactionEventPublisher(KafkaTemplate<String, TransactionEvent> kafka) {
        this.kafka = kafka;
    }

    public void publish(TransactionEvent event) {
        kafka.send("transactions.completed", event.accountId(), event)
             .whenComplete((result, err) -> {
                 if (err != null) {
                     log.error("Failed to publish transactionEvent {} for account {}",
                               event.eventId(), event.accountId(), err);
                 } else {
                     log.debug("Published transactionEvent {} to {}-{}@{}",
                               event.eventId(),
                               result.getRecordMetadata().topic(),
                               result.getRecordMetadata().partition(),
                               result.getRecordMetadata().offset());
                 }
             });
    }
}
```

`KafkaTemplate.send(...)` is non-blocking (returns `CompletableFuture`). For the capstone's scale, fire-and-forget with logging is acceptable. If your team chooses to await the future and block, that's fine too — call out the trade-off in `docs/architecture.md`.

## When to publish

**After the database transaction commits.** Two acceptable approaches:

### Option A — publish from the service after `@Transactional` returns

```java
@Service
public class TransactionService {
    @Transactional
    public TransactionDto submit(NewTransactionRequest req, String callerUserId) {
        // ... debit account, insert TRANSACTIONS row(s), maybe call Payment Processor
        return TransactionDto.from(saved);
    }
}

// In the controller (outside the @Transactional boundary):
@PostMapping
public ResponseEntity<TransactionDto> create(@Valid @RequestBody NewTransactionRequest req,
                                             @AuthenticationPrincipal Jwt jwt) {
    TransactionDto created = transactionService.submit(req, callerId(jwt));
    eventPublisher.publish(TransactionEvent.from(created));
    return ResponseEntity.created(...).body(created);
}
```

Simple, easy to read, easy to test.

### Option B — `@TransactionalEventListener`

Have the service publish a Spring `ApplicationEvent` and let a `@TransactionalEventListener(phase = AFTER_COMMIT)` translate it into a Kafka publish. Cleaner separation, slightly more setup.

Pick one approach for the team and stick with it.

## What if Kafka is down?

The broker is provided by the instructor and should be up. If it's not:

- **Don't roll back the database transaction.** The user's transaction *did* complete; they shouldn't see a 500 because a downstream message bus burped.
- **Do log the failure** at ERROR with the `eventId` and `transactionId` so an operator could replay later.
- For "Exceeds" credit, write a `TransactionEventOutbox` row in the same DB transaction and have a scheduled job re-publish failed events. Don't build this on Day 1.

## Verifying you're publishing correctly

The instructor will provide a console consumer command. Roughly:

```bash
kafka-console-consumer.sh \
  --bootstrap-server <broker-url> \
  --topic transactions.completed \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" | "
```

Submit a transaction in the SPA, watch the consumer print one (or two, for transfers) JSON events. Include this in your Day-3 demo.

## Tests for the Kafka path

- **Unit** — mock `KafkaTemplate`, verify `publish` is called with the right topic, key, and payload after a successful service call.
- **Integration** — `@EmbeddedKafka` (provided by `spring-kafka-test`) lets you spin up an in-process broker for a single test class. One test that submits a transaction via MockMvc and consumes the resulting event covers the full round-trip. The rubric's "integration tests cover Kafka event emission" line points at exactly this test.

Next: [Testing & Security Validation](./07-testing.md).
