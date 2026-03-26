package com.orchestrator.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Saga for P2P money transfer across microservices:
 *  1. Validate sender (auth service)
 *  2. Debit sender wallet (wallet service)
 *  3. Credit receiver wallet (wallet service)
 *  4. Record transaction (ledger service)
 *  5. Send notification (notification service)
 *
 * If step 3 fails, step 2 is compensated (refund sender).
 */
@Component
@Slf4j
public class TransferSaga implements SagaDefinition {

    private final Random random = new Random();

    @Override
    public String getType() { return "P2P_TRANSFER"; }

    @Override
    public List<SagaStep> getSteps() {
        return List.of(
                new ValidateSenderStep(),
                new DebitSenderStep(),
                new CreditReceiverStep(),
                new RecordTransactionStep(),
                new SendNotificationStep()
        );
    }

    class ValidateSenderStep implements SagaStep {
        @Override public String getName() { return "VALIDATE_SENDER"; }

        @Override
        public StepOutcome execute(Map<String, Object> ctx) {
            String sender = (String) ctx.getOrDefault("sender", "");
            if (sender.isBlank()) return StepOutcome.failure("Sender not specified");
            ctx.put("senderValidated", true);
            return StepOutcome.success("Sender validated: " + sender);
        }

        @Override
        public StepOutcome compensate(Map<String, Object> ctx) {
            return StepOutcome.success("No compensation needed for validation");
        }
    }

    class DebitSenderStep implements SagaStep {
        @Override public String getName() { return "DEBIT_SENDER"; }

        @Override
        public StepOutcome execute(Map<String, Object> ctx) {
            double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
            if (amount <= 0) return StepOutcome.failure("Invalid amount");
            ctx.put("senderDebited", true);
            return StepOutcome.success("Debited " + amount + " from sender");
        }

        @Override
        public StepOutcome compensate(Map<String, Object> ctx) {
            double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
            ctx.put("senderDebited", false);
            return StepOutcome.success("Refunded " + amount + " to sender");
        }
    }

    class CreditReceiverStep implements SagaStep {
        @Override public String getName() { return "CREDIT_RECEIVER"; }

        @Override
        public StepOutcome execute(Map<String, Object> ctx) {
            // Simulate 80% success rate (receiver might be inactive, etc.)
            boolean forceFailure = Boolean.TRUE.equals(ctx.get("simulateFailure"));
            if (forceFailure) {
                return StepOutcome.failure("Receiver account is suspended");
            }
            double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
            ctx.put("receiverCredited", true);
            return StepOutcome.success("Credited " + amount + " to receiver");
        }

        @Override
        public StepOutcome compensate(Map<String, Object> ctx) {
            double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
            ctx.put("receiverCredited", false);
            return StepOutcome.success("Reversed " + amount + " from receiver");
        }
    }

    class RecordTransactionStep implements SagaStep {
        @Override public String getName() { return "RECORD_TRANSACTION"; }

        @Override
        public StepOutcome execute(Map<String, Object> ctx) {
            String txnId = "TXN-" + System.currentTimeMillis();
            ctx.put("transactionId", txnId);
            return StepOutcome.success("Transaction recorded: " + txnId);
        }

        @Override
        public StepOutcome compensate(Map<String, Object> ctx) {
            String txnId = (String) ctx.get("transactionId");
            return StepOutcome.success("Transaction reversed: " + txnId);
        }
    }

    class SendNotificationStep implements SagaStep {
        @Override public String getName() { return "SEND_NOTIFICATION"; }

        @Override
        public StepOutcome execute(Map<String, Object> ctx) {
            return StepOutcome.success("SMS sent to sender and receiver");
        }

        @Override
        public StepOutcome compensate(Map<String, Object> ctx) {
            return StepOutcome.success("No compensation needed for notifications");
        }
    }
}
