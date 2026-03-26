# Service Orchestrator

A saga-based distributed transaction coordinator. Executes multi-service operations — P2P transfers, customer onboarding — as ordered step sequences, and automatically compensates (rolls back) all completed steps in reverse order when any step fails. Maintains data consistency across microservices without distributed locks or two-phase commit.

In a microservices architecture, a single business operation (e.g., "send money") touches 4–5 services. If service #3 fails, services #1 and #2 have already committed their changes. The Saga pattern solves this by defining a compensating action for every forward action, guaranteeing that the system returns to a consistent state even on partial failure.

## Why This Architecture

| Problem | Solution | Implementation |
|---|---|---|
| Transfer debits sender but fails to credit receiver | Compensating transactions | Every `SagaStep` has `execute()` and `compensate()` — failure triggers reverse-order rollback |
| Don't know which steps completed before failure | Step execution logging | Every execute and compensate logged with result, duration, and detail |
| Compensation itself can fail | Compensation failure tracking | If compensation fails, saga enters FAILED state for manual intervention |
| Need different sagas for different operations | Pluggable saga definitions | Implement `SagaDefinition` + `SagaStep` interfaces — auto-registered via Spring |
| Can't measure reliability | Saga statistics | Success rate, completion count, compensation count tracked per saga type |

## Built-In Sagas

### P2P Transfer (5 steps across 4 services)

| # | Step | Service | Execute | Compensate |
|---|---|---|---|---|
| 1 | Validate Sender | Auth | Verify identity | — |
| 2 | Debit Sender | Wallet | Deduct amount | Refund amount |
| 3 | Credit Receiver | Wallet | Add amount | Reverse credit |
| 4 | Record Transaction | Ledger | Write record | Reverse record |
| 5 | Send Notification | Notification | Send SMS | — |

### Customer Onboarding (5 steps across 5 services)

| # | Step | Service | Execute | Compensate |
|---|---|---|---|---|
| 1 | Create Profile | CRM | Create customer | Delete profile |
| 2 | KYC Verification | KYC | Run checks | Remove KYC record |
| 3 | Create Wallet | Wallet | Open account | Deactivate wallet |
| 4 | Welcome Bonus | Promotions | Credit KES 50 | Reverse bonus |
| 5 | Welcome SMS | Notification | Send message | — |

## How Compensation Works

```
Happy path:
  Step 1 ✓ → Step 2 ✓ → Step 3 ✓ → Step 4 ✓ → Step 5 ✓ → COMPLETED

Failure at Step 3:
  Step 1 ✓ → Step 2 ✓ → Step 3 ✗ (FAILED)
                          ↓
  Compensate Step 2 ✓ ← Compensate Step 1 ✓ → COMPENSATED

Compensation failure:
  Step 1 ✓ → Step 2 ✓ → Step 3 ✗ (FAILED)
                          ↓
  Compensate Step 2 ✗ → FAILED (requires manual intervention)
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/sagas/execute?sagaType=P2P_TRANSFER` | Execute a saga with context |
| GET | `/api/sagas/{sagaId}` | Saga instance status |
| GET | `/api/sagas/{sagaId}/logs` | Step execution + compensation logs |
| GET | `/api/sagas/stats` | Success/compensation/failure counts |

## Usage

```bash
# Execute a P2P transfer saga
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000}'

# Execute with simulated failure (triggers compensation)
curl -X POST "http://localhost:8686/api/sagas/execute?sagaType=P2P_TRANSFER" \
  -H "Content-Type: application/json" \
  -d '{"sender":"+254700000001","receiver":"+254700000002","amount":5000,"simulateFailure":true}'

# View compensation logs
curl http://localhost:8686/api/sagas/SAGA-XXXXXXXXXX/logs
```

## Extending with New Sagas

```java
@Component
public class MyCustomSaga implements SagaDefinition {
    @Override public String getType() { return "MY_SAGA"; }
    @Override public List<SagaStep> getSteps() {
        return List.of(
            new SagaStep() {
                public String getName() { return "STEP_ONE"; }
                public StepOutcome execute(Map<String, Object> ctx) { /* ... */ }
                public StepOutcome compensate(Map<String, Object> ctx) { /* ... */ }
            }
        );
    }
}
```

Any `@Component` implementing `SagaDefinition` is auto-registered at startup.

## Running

```bash
mvn spring-boot:run   # http://localhost:8686/swagger-ui.html
```

## Testing

```bash
mvn test   # 11 tests
```

Covers: successful P2P transfer, credit failure with compensation, compensation logging, customer onboarding success, KYC failure compensation, wallet failure with correct rollback count, unknown saga rejection, retrieval by ID, stats, sender validation, step duration tracking.

## License

MIT
