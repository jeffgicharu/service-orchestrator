# Service Orchestrator

Saga-based distributed transaction orchestrator. Coordinates multi-service operations (P2P transfers, customer onboarding) with automatic compensating transactions when any step fails. Implements the Saga pattern for maintaining data consistency across microservices without distributed locks.

## Features

- **Saga Pattern** - Orchestration-based sagas with forward execution and reverse compensation
- **Compensating Transactions** - Failed steps trigger automatic rollback of all completed steps in reverse order
- **Built-in Sagas** - P2P Transfer (5 steps across 4 services), Customer Onboarding (5 steps across 5 services)
- **Extensible Framework** - Implement `SagaDefinition` + `SagaStep` to add new sagas
- **Step Logging** - Every execution and compensation logged with duration and result
- **Pluggable Steps** - Each step has independent execute() and compensate() methods
- **Statistics** - Success rate, completion counts, compensation tracking

## Architecture

```
API Request → SagaOrchestrator → Step 1 (execute) → Step 2 (execute) → Step 3 (FAILS!)
                                                                           ↓
                                  Step 1 (compensate) ← Step 2 (compensate) ←
```

## P2P Transfer Saga Steps

| Step | Service | Execute | Compensate |
|---|---|---|---|
| 1 | Auth | Validate sender | - |
| 2 | Wallet | Debit sender | Refund sender |
| 3 | Wallet | Credit receiver | Reverse credit |
| 4 | Ledger | Record transaction | Reverse transaction |
| 5 | Notification | Send SMS | - |

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/sagas/execute?sagaType=P2P_TRANSFER` | Execute a saga |
| GET | `/api/sagas/{sagaId}` | Get saga status |
| GET | `/api/sagas/{sagaId}/logs` | Step execution logs |
| GET | `/api/sagas/stats` | Orchestrator statistics |

## Running

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8686/swagger-ui.html
```

## Tests

```bash
mvn test  # 11 tests
```

## License

MIT
