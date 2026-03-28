# Service Orchestrator

When a customer sends money, the system has to do five things: validate the sender, debit their wallet, credit the receiver, record the transaction, and send an SMS. Five different services, five different databases. If the third one fails, the first two have already committed — the sender's money is gone but the receiver never got it.

You can't just rollback across services like you would inside a single database. Instead, you need each step to have a way to undo itself. That's what this project does. It runs through a sequence of steps, and if any step fails, it goes back and undoes every step that already succeeded — in reverse order.

This is called the **Saga pattern**, and it's how real distributed systems handle transactions without locking everything.

## What It Does

- **Runs multi-step operations** across services as an ordered sequence
- **Automatically rolls back** when something fails — every completed step gets compensated in reverse order
- **Logs every action** — both forward executions and compensations, with timing and results
- **Ships with two built-in sagas** you can run immediately
- **Easy to extend** — add a new saga by implementing two interfaces and annotating with `@Component`

## Built-In Sagas

### P2P Money Transfer

Simulates sending money across four different services:

| Step | What it does | If it needs to undo |
|---|---|---|
| 1. Validate Sender | Checks the sender exists | Nothing to undo |
| 2. Debit Sender | Takes money from sender's wallet | Refunds the money |
| 3. Credit Receiver | Adds money to receiver's wallet | Reverses the credit |
| 4. Record Transaction | Writes to the ledger | Removes the record |
| 5. Send Notification | Sends confirmation SMS | Nothing to undo |

If step 3 fails (say the receiver's account is suspended), step 2 gets compensated automatically — the sender gets their money back.

### Customer Onboarding

Simulates registering a new customer across five services:

| Step | What it does | If it needs to undo |
|---|---|---|
| 1. Create Profile | Creates customer in CRM | Deletes the profile |
| 2. KYC Verification | Runs identity checks | Removes KYC record |
| 3. Create Wallet | Opens a new wallet | Deactivates the wallet |
| 4. Welcome Bonus | Credits KES 50 | Reverses the bonus |
| 5. Welcome SMS | Sends welcome message | Nothing to undo |

## What Happens When Things Fail

**Everything works:**
```
Step 1 ✓ → Step 2 ✓ → Step 3 ✓ → Step 4 ✓ → Step 5 ✓ → COMPLETED
```

**Step 3 fails:**
```
Step 1 ✓ → Step 2 ✓ → Step 3 ✗
                        ↓
Undo Step 2 ✓ → Undo Step 1 ✓ → COMPENSATED
```

**Step 3 fails AND the undo for Step 2 also fails:**
```
Step 1 ✓ → Step 2 ✓ → Step 3 ✗
                        ↓
Undo Step 2 ✗ → FAILED (needs manual intervention)
```

The system never leaves data in an inconsistent state silently. It either completes, fully compensates, or marks itself as FAILED so someone can investigate.

## Quick Start

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8686/swagger-ui.html
```

## Try It Out

```bash
# Run a successful transfer
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000}'

# Run a transfer that fails at step 3 (triggers compensation)
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000,"simulateFailure":true}'

# See the compensation in the logs
curl http://localhost:8686/api/sagas/SAGA-XXXXXXXXXX/logs
```

## Adding Your Own Saga

Create a class that implements `SagaDefinition`, define your steps, and annotate with `@Component`. It gets registered automatically at startup:

```java
@Component
public class MyCustomSaga implements SagaDefinition {
    public String getType() { return "MY_SAGA"; }
    public List<SagaStep> getSteps() {
        return List.of(/* your steps */);
    }
}
```

Each step implements `execute()` (the forward action) and `compensate()` (how to undo it).

## API Reference

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/sagas/execute?sagaType=P2P_TRANSFER` | Run a saga |
| GET | `/api/sagas/{sagaId}` | Check saga status |
| GET | `/api/sagas/{sagaId}/logs` | Step execution and compensation logs |
| GET | `/api/sagas/stats` | Success/compensation/failure counts |

## Built With

Spring Boot 3.2, Java 17, Spring Data JPA, PostgreSQL (H2 for dev), Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 11 tests
```

Covers successful P2P transfer, credit failure with compensation, compensation logging, customer onboarding (success + KYC failure + wallet failure with correct rollback count), unknown saga rejection, retrieval by ID, stats, sender validation, and step duration tracking.

## License

MIT
