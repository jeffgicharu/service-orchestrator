package com.orchestrator.saga;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.entity.SagaStepLog;
import com.orchestrator.enums.SagaStatus;
import com.orchestrator.enums.StepResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SagaOrchestratorTest {

    @Autowired private SagaOrchestrator orchestrator;

    @Test
    @DisplayName("Should complete P2P transfer saga successfully")
    void transfer_success() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "sender", "+254700000001", "receiver", "+254700000002", "amount", 5000));
        SagaInstance result = orchestrator.execute("P2P_TRANSFER", ctx);

        assertEquals(SagaStatus.COMPLETED, result.getStatus());
        assertEquals(5, result.getTotalSteps());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("Should compensate when credit receiver fails")
    void transfer_failure_compensates() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "sender", "+254700000001", "receiver", "+254700000002",
                "amount", 5000, "simulateFailure", true));
        SagaInstance result = orchestrator.execute("P2P_TRANSFER", ctx);

        assertEquals(SagaStatus.COMPENSATED, result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("CREDIT_RECEIVER"));
    }

    @Test
    @DisplayName("Should log all steps including compensations")
    void transfer_failure_logsCompensations() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "sender", "+254700000001", "receiver", "+254700000002",
                "amount", 5000, "simulateFailure", true));
        SagaInstance result = orchestrator.execute("P2P_TRANSFER", ctx);

        List<SagaStepLog> logs = orchestrator.getLogs(result.getSagaId());
        assertFalse(logs.isEmpty());

        // Should have EXECUTE logs for steps 1-3, then COMPENSATE logs for steps 2-1
        assertTrue(logs.stream().anyMatch(l -> l.getAction().equals("COMPENSATE")));
        assertTrue(logs.stream().anyMatch(l -> l.getResult() == StepResult.COMPENSATED));
    }

    @Test
    @DisplayName("Should complete customer onboarding saga")
    void onboarding_success() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "customerName", "Jane Doe", "phone", "+254700000003"));
        SagaInstance result = orchestrator.execute("CUSTOMER_ONBOARDING", ctx);

        assertEquals(SagaStatus.COMPLETED, result.getStatus());
        assertEquals(5, result.getTotalSteps());
    }

    @Test
    @DisplayName("Should compensate onboarding when KYC fails")
    void onboarding_kycFails_compensates() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "customerName", "Jane Doe", "failAtStep", "KYC_VERIFICATION"));
        SagaInstance result = orchestrator.execute("CUSTOMER_ONBOARDING", ctx);

        assertEquals(SagaStatus.COMPENSATED, result.getStatus());
        assertTrue(result.getFailureReason().contains("KYC_VERIFICATION"));
    }

    @Test
    @DisplayName("Should compensate onboarding when wallet creation fails")
    void onboarding_walletFails_compensatesPriorSteps() {
        Map<String, Object> ctx = new HashMap<>(Map.of(
                "customerName", "Jane Doe", "failAtStep", "CREATE_WALLET"));
        SagaInstance result = orchestrator.execute("CUSTOMER_ONBOARDING", ctx);

        assertEquals(SagaStatus.COMPENSATED, result.getStatus());
        List<SagaStepLog> logs = orchestrator.getLogs(result.getSagaId());
        long compensations = logs.stream().filter(l -> l.getAction().equals("COMPENSATE")).count();
        assertEquals(2, compensations); // profile + KYC compensated
    }

    @Test
    @DisplayName("Should reject unknown saga type")
    void execute_unknownType_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                orchestrator.execute("NONEXISTENT", Map.of()));
    }

    @Test
    @DisplayName("Should retrieve saga by ID")
    void getById_returnsSaga() {
        Map<String, Object> ctx = new HashMap<>(Map.of("sender", "test", "receiver", "test2", "amount", 100));
        SagaInstance created = orchestrator.execute("P2P_TRANSFER", ctx);
        SagaInstance retrieved = orchestrator.getById(created.getSagaId());
        assertEquals(created.getSagaId(), retrieved.getSagaId());
    }

    @Test
    @DisplayName("Should return stats")
    void getStats_valid() {
        Map<String, Object> ctx = new HashMap<>(Map.of("sender", "s", "receiver", "r", "amount", 100));
        orchestrator.execute("P2P_TRANSFER", ctx);
        var stats = orchestrator.getStats();
        assertTrue((long) stats.get("total") >= 1);
    }

    @Test
    @DisplayName("Should validate sender in transfer saga")
    void transfer_noSender_fails() {
        Map<String, Object> ctx = new HashMap<>(Map.of("receiver", "+254700000002", "amount", 5000));
        ctx.put("sender", "");
        SagaInstance result = orchestrator.execute("P2P_TRANSFER", ctx);
        assertEquals(SagaStatus.COMPENSATED, result.getStatus());
        assertTrue(result.getFailureReason().contains("VALIDATE_SENDER"));
    }

    @Test
    @DisplayName("Should track step durations")
    void transfer_logsDuration() {
        Map<String, Object> ctx = new HashMap<>(Map.of("sender", "s", "receiver", "r", "amount", 100));
        SagaInstance result = orchestrator.execute("P2P_TRANSFER", ctx);
        List<SagaStepLog> logs = orchestrator.getLogs(result.getSagaId());
        logs.forEach(l -> assertTrue(l.getDurationMs() >= 0));
    }
}
