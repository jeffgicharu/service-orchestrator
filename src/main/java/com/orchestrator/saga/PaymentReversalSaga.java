package com.orchestrator.saga;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Saga for reversing a completed payment:
 *  1. Validate the original transaction exists and is eligible for reversal
 *  2. Reverse the credit from the receiver's wallet
 *  3. Refund the debit to the sender's wallet
 *  4. Void the ledger entry and record the reversal
 *  5. Send reversal confirmation notifications to both parties
 *
 * This is the inverse of the transfer saga. If the refund to the sender
 * fails after the receiver has been reversed, the saga compensates by
 * re-crediting the receiver.
 */
@Component
public class PaymentReversalSaga implements SagaDefinition {

    @Override
    public String getType() { return "PAYMENT_REVERSAL"; }

    @Override
    public List<SagaStep> getSteps() {
        return List.of(
                new SagaStep() {
                    @Override public String getName() { return "VALIDATE_TRANSACTION"; }
                    @Override public StepOutcome execute(Map<String, Object> ctx) {
                        String txnId = (String) ctx.get("transactionId");
                        if (txnId == null || txnId.isBlank()) return StepOutcome.failure("Transaction ID required");
                        String status = (String) ctx.getOrDefault("originalStatus", "COMPLETED");
                        if (!"COMPLETED".equals(status) && !"SETTLED".equals(status)) {
                            return StepOutcome.failure("Transaction is not eligible for reversal. Status: " + status);
                        }
                        return StepOutcome.success("Transaction " + txnId + " validated for reversal");
                    }
                    @Override public StepOutcome compensate(Map<String, Object> ctx) {
                        return StepOutcome.success("No compensation needed for validation");
                    }
                },
                new SagaStep() {
                    @Override public String getName() { return "REVERSE_RECEIVER_CREDIT"; }
                    @Override public StepOutcome execute(Map<String, Object> ctx) {
                        boolean forceFailure = "REVERSE_RECEIVER_CREDIT".equals(ctx.get("failAtStep"));
                        if (forceFailure) return StepOutcome.failure("Receiver wallet service unavailable");
                        double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
                        ctx.put("receiverReversed", true);
                        return StepOutcome.success("Reversed " + amount + " from receiver");
                    }
                    @Override public StepOutcome compensate(Map<String, Object> ctx) {
                        double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
                        ctx.put("receiverReversed", false);
                        return StepOutcome.success("Re-credited " + amount + " to receiver");
                    }
                },
                new SagaStep() {
                    @Override public String getName() { return "REFUND_SENDER_DEBIT"; }
                    @Override public StepOutcome execute(Map<String, Object> ctx) {
                        boolean forceFailure = "REFUND_SENDER_DEBIT".equals(ctx.get("failAtStep"));
                        if (forceFailure) return StepOutcome.failure("Sender wallet service unavailable");
                        double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
                        ctx.put("senderRefunded", true);
                        return StepOutcome.success("Refunded " + amount + " to sender");
                    }
                    @Override public StepOutcome compensate(Map<String, Object> ctx) {
                        double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
                        ctx.put("senderRefunded", false);
                        return StepOutcome.success("Re-debited " + amount + " from sender");
                    }
                },
                new SagaStep() {
                    @Override public String getName() { return "VOID_LEDGER_ENTRY"; }
                    @Override public StepOutcome execute(Map<String, Object> ctx) {
                        String txnId = (String) ctx.get("transactionId");
                        ctx.put("ledgerVoided", true);
                        return StepOutcome.success("Ledger entry voided for " + txnId);
                    }
                    @Override public StepOutcome compensate(Map<String, Object> ctx) {
                        return StepOutcome.success("Ledger void reversed");
                    }
                },
                new SagaStep() {
                    @Override public String getName() { return "SEND_REVERSAL_NOTIFICATIONS"; }
                    @Override public StepOutcome execute(Map<String, Object> ctx) {
                        return StepOutcome.success("Reversal SMS sent to sender and receiver");
                    }
                    @Override public StepOutcome compensate(Map<String, Object> ctx) {
                        return StepOutcome.success("No compensation needed for notifications");
                    }
                }
        );
    }
}
