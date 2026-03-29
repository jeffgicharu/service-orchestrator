# Service Orchestrator

When a customer sends money, the system has to do five things: validate the sender, debit their wallet, credit the receiver, record the transaction, and send an SMS. Five different services, five different databases. If the third one fails, the first two have already committed, and the sender's money is gone but the receiver never got it.

You can't just rollback across services like you would inside a single database. Instead, you need each step to have a way to undo itself. That's what this project does. It runs through a sequence of steps, and if any step fails, it goes back and undoes every step that already succeeded, in reverse order. If a saga fails entirely (compensation itself breaks), you can retry it through the API.

## What It Does

**Saga execution:**
- Runs multi-step distributed operations as an ordered sequence
- Automatically compensates all completed steps in reverse order when any step fails
- Saves a context snapshot at every step so you can see exactly what data each step had when it ran
- Three built-in sagas: P2P transfer, customer onboarding, and payment reversal

**Failure handling:**
- If compensation itself fails, the saga enters FAILED state for manual investigation
- Failed or compensated sagas can be retried through the API
- Every execution and compensation is logged with duration, result, and context

**Observability:**
- Paginated saga history with filtering by status and type
- Per-saga-type statistics showing success rates per flow
- List all registered saga types with their step names
- Step-level logs with context snapshots for debugging

## Built-In Sagas

### P2P Money Transfer (5 steps)

| Step | What it does | If it needs to undo |
|---|---|---|
| 1. Validate Sender | Checks the sender exists | Nothing to undo |
| 2. Debit Sender | Takes money from sender's wallet | Refunds the money |
| 3. Credit Receiver | Adds money to receiver's wallet | Reverses the credit |
| 4. Record Transaction | Writes to the ledger | Removes the record |
| 5. Send Notification | Sends confirmation SMS | Nothing to undo |

### Customer Onboarding (5 steps)

| Step | What it does | If it needs to undo |
|---|---|---|
| 1. Create Profile | Creates customer in CRM | Deletes the profile |
| 2. KYC Verification | Runs identity checks | Removes KYC record |
| 3. Create Wallet | Opens a new wallet | Deactivates the wallet |
| 4. Welcome Bonus | Credits KES 50 | Reverses the bonus |
| 5. Welcome SMS | Sends welcome message | Nothing to undo |

### Payment Reversal (5 steps)

| Step | What it does | If it needs to undo |
|---|---|---|
| 1. Validate Transaction | Checks eligibility | Nothing to undo |
| 2. Reverse Receiver Credit | Takes back money from receiver | Re-credits the receiver |
| 3. Refund Sender Debit | Returns money to sender | Re-debits the sender |
| 4. Void Ledger Entry | Marks ledger as voided | Restores the ledger |
| 5. Send Notifications | Sends reversal SMS to both | Nothing to undo |

## What Happens When Things Fail

**Everything works:**
```
Step 1 OK -> Step 2 OK -> Step 3 OK -> Step 4 OK -> Step 5 OK -> COMPLETED
```

**Step 3 fails:**
```
Step 1 OK -> Step 2 OK -> Step 3 FAIL -> Undo Step 2 OK -> Undo Step 1 OK -> COMPENSATED
```

**Compensation also fails:**
```
Step 1 OK -> Step 2 OK -> Step 3 FAIL -> Undo Step 2 FAIL -> FAILED (retry via API)
```

## Quick Start

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8686/swagger-ui.html
```

## Try It Out

```bash
# Successful transfer
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000}'

# Failed transfer (triggers compensation)
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000,"simulateFailure":true}'

# Payment reversal
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=PAYMENT_REVERSAL" \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"TXN-12345","originalStatus":"COMPLETED","amount":3000}'

# Step logs with context snapshots
curl http://localhost:8686/api/sagas/SAGA-XXXXXXXXXX/logs

# Retry a failed saga
curl -X POST http://localhost:8686/api/sagas/SAGA-XXXXXXXXXX/retry

# Browse history
curl "http://localhost:8686/api/sagas?size=10"

# Per-type stats
curl http://localhost:8686/api/sagas/stats
```

## API Reference

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/sagas/execute?sagaType=...` | Execute a saga |
| GET | `/api/sagas/{sagaId}` | Get saga details |
| GET | `/api/sagas/{sagaId}/logs` | Step logs with context snapshots |
| POST | `/api/sagas/{sagaId}/retry` | Retry a failed/compensated saga |
| GET | `/api/sagas` | List all sagas (paginated) |
| GET | `/api/sagas/status/{status}` | Filter by status |
| GET | `/api/sagas/type/{type}` | Filter by saga type |
| GET | `/api/sagas/stats` | Stats with per-type breakdown |
| GET | `/api/sagas/types` | List registered saga types and steps |

## Adding Your Own Saga

```java
@Component
public class MyCustomSaga implements SagaDefinition {
    public String getType() { return "MY_SAGA"; }
    public List<SagaStep> getSteps() {
        return List.of(
            new SagaStep() {
                public String getName() { return "MY_STEP"; }
                public StepOutcome execute(Map<String, Object> ctx) { /* forward */ }
                public StepOutcome compensate(Map<String, Object> ctx) { /* undo */ }
            }
        );
    }
}
```

## Built With

Spring Boot 3.2, Java 17, Spring Data JPA, PostgreSQL (H2 for dev), Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 25 tests
```

**Unit tests (11):** P2P transfer success, credit failure with compensation, compensation logging, customer onboarding success/failure, wallet failure with correct rollback count, unknown type rejection, retrieval by ID, stats, sender validation, step durations.

**Integration tests (14):** P2P transfer via HTTP, failed transfer with conflict status, customer onboarding, payment reversal success/failure, get by ID, step logs with context snapshots, saga retry, paginated listing, status filtering, type filtering, per-type stats, registered types, unknown type rejection.

## License

MIT
