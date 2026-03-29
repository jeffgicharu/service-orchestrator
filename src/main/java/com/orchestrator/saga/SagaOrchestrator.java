package com.orchestrator.saga;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.entity.SagaStepLog;
import com.orchestrator.enums.SagaStatus;
import com.orchestrator.enums.StepResult;
import com.orchestrator.repository.SagaInstanceRepository;
import com.orchestrator.repository.SagaStepLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core saga orchestrator. Executes saga steps in order.
 * If any step fails, compensates all previously completed steps in reverse order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaInstanceRepository sagaRepo;
    private final SagaStepLogRepository stepLogRepo;
    private final Map<String, SagaDefinition> sagaDefinitions = new HashMap<>();

    public void registerSaga(SagaDefinition definition) {
        sagaDefinitions.put(definition.getType(), definition);
        log.info("Registered saga: {} ({} steps)", definition.getType(), definition.getSteps().size());
    }

    @Transactional
    public SagaInstance execute(String sagaType, Map<String, Object> context) {
        SagaDefinition definition = sagaDefinitions.get(sagaType);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown saga type: " + sagaType);
        }

        List<SagaStep> steps = definition.getSteps();
        String sagaId = "SAGA-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        SagaInstance saga = SagaInstance.builder()
                .sagaId(sagaId)
                .sagaType(sagaType)
                .status(SagaStatus.STARTED)
                .currentStep(0)
                .totalSteps(steps.size())
                .payload(context.toString())
                .build();
        sagaRepo.save(saga);

        log.info("Saga started: {} [{}] ({} steps)", sagaId, sagaType, steps.size());

        List<Integer> completedSteps = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            saga.setCurrentStep(i + 1);
            saga.setStatus(SagaStatus.IN_PROGRESS);
            sagaRepo.save(saga);

            long start = System.currentTimeMillis();
            SagaStep.StepOutcome outcome = step.execute(context);
            long duration = System.currentTimeMillis() - start;

            logStep(saga, i + 1, step.getName(), "EXECUTE",
                    outcome.success() ? StepResult.SUCCESS : StepResult.FAILURE,
                    duration, outcome.detail(), context);

            if (outcome.success()) {
                completedSteps.add(i);
                log.info("  Step {}/{} [{}]: SUCCESS ({}ms)", i + 1, steps.size(), step.getName(), duration);
            } else {
                log.warn("  Step {}/{} [{}]: FAILED - {}", i + 1, steps.size(), step.getName(), outcome.detail());
                saga.setFailureReason("Step '" + step.getName() + "' failed: " + outcome.detail());

                // Compensate completed steps in reverse order
                compensate(saga, steps, completedSteps, context);
                return saga;
            }
        }

        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepo.save(saga);

        log.info("Saga completed: {} [{}]", sagaId, sagaType);
        return saga;
    }

    private void compensate(SagaInstance saga, List<SagaStep> steps,
                            List<Integer> completedSteps, Map<String, Object> context) {
        saga.setStatus(SagaStatus.COMPENSATING);
        sagaRepo.save(saga);

        log.info("Compensating {} completed steps...", completedSteps.size());

        // Reverse order compensation
        Collections.reverse(completedSteps);

        for (int stepIndex : completedSteps) {
            SagaStep step = steps.get(stepIndex);
            long start = System.currentTimeMillis();

            try {
                SagaStep.StepOutcome outcome = step.compensate(context);
                long duration = System.currentTimeMillis() - start;

                logStep(saga, stepIndex + 1, step.getName(), "COMPENSATE",
                        StepResult.COMPENSATED, duration, outcome.detail(), context);

                log.info("  Compensated step [{}] ({}ms)", step.getName(), duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                logStep(saga, stepIndex + 1, step.getName(), "COMPENSATE",
                        StepResult.FAILURE, duration, "Compensation failed: " + e.getMessage(), context);

                log.error("  Compensation FAILED for step [{}]: {}", step.getName(), e.getMessage());
                saga.setStatus(SagaStatus.FAILED);
                sagaRepo.save(saga);
                return;
            }
        }

        saga.setStatus(SagaStatus.COMPENSATED);
        saga.setCompletedAt(LocalDateTime.now());
        sagaRepo.save(saga);

        log.info("Saga fully compensated: {}", saga.getSagaId());
    }

    // ─── QUERIES ────────────────────────────────────────────────────

    public SagaInstance getById(String sagaId) {
        return sagaRepo.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
    }

    public List<SagaStepLog> getLogs(String sagaId) {
        SagaInstance saga = getById(sagaId);
        return stepLogRepo.findBySagaIdOrderByStepOrder(saga.getId());
    }

    public org.springframework.data.domain.Page<SagaInstance> listAll(org.springframework.data.domain.Pageable pageable) {
        return sagaRepo.findAllByOrderByStartedAtDesc(pageable);
    }

    public org.springframework.data.domain.Page<SagaInstance> listByStatus(SagaStatus status, org.springframework.data.domain.Pageable pageable) {
        return sagaRepo.findByStatusOrderByStartedAtDesc(status, pageable);
    }

    public org.springframework.data.domain.Page<SagaInstance> listByType(String sagaType, org.springframework.data.domain.Pageable pageable) {
        return sagaRepo.findBySagaTypeOrderByStartedAtDesc(sagaType, pageable);
    }

    // ─── RETRY ──────────────────────────────────────────────────────

    @Transactional
    public SagaInstance retry(String sagaId) {
        SagaInstance original = getById(sagaId);
        if (original.getStatus() != SagaStatus.FAILED && original.getStatus() != SagaStatus.COMPENSATED) {
            throw new IllegalStateException("Only FAILED or COMPENSATED sagas can be retried. Current: " + original.getStatus());
        }

        // Parse the original context back from the stored payload
        Map<String, Object> context = new HashMap<>();
        // The payload is stored as a Map.toString(), so we rebuild basic context
        context.put("retryOf", original.getSagaId());
        context.put("originalPayload", original.getPayload());

        // Re-execute with same saga type
        return execute(original.getSagaType(), context);
    }

    // ─── STATS ──────────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        long total = sagaRepo.count();
        long completed = sagaRepo.countByStatus(SagaStatus.COMPLETED);
        long compensated = sagaRepo.countByStatus(SagaStatus.COMPENSATED);
        long failed = sagaRepo.countByStatus(SagaStatus.FAILED);
        long inProgress = sagaRepo.countByStatus(SagaStatus.IN_PROGRESS);

        // Per-type breakdown
        Map<String, Map<String, Long>> byType = new java.util.LinkedHashMap<>();
        for (Object[] row : sagaRepo.countByTypeAndStatus()) {
            String type = row[0].toString();
            String status = row[1].toString();
            long count = (Long) row[2];
            byType.computeIfAbsent(type, k -> new java.util.LinkedHashMap<>()).put(status, count);
        }

        return Map.of(
                "total", total,
                "completed", completed,
                "compensated", compensated,
                "failed", failed,
                "inProgress", inProgress,
                "successRate", total > 0 ? Math.round((double) completed / total * 100) : 0,
                "byType", byType
        );
    }

    // ─── REGISTERED SAGA TYPES ──────────────────────────────────────

    public List<Map<String, Object>> getRegisteredTypes() {
        return sagaDefinitions.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "type", e.getKey(),
                        "steps", e.getValue().getSteps().size(),
                        "stepNames", e.getValue().getSteps().stream().map(SagaStep::getName).toList()
                ))
                .toList();
    }

    // ─── HELPERS ────────────────────────────────────────────────────

    private void logStep(SagaInstance saga, int order, String name, String action,
                         StepResult result, long duration, String detail,
                         Map<String, Object> context) {
        String snapshot = null;
        try {
            snapshot = context.toString();
            if (snapshot.length() > 2000) snapshot = snapshot.substring(0, 2000);
        } catch (Exception ignored) {}

        stepLogRepo.save(SagaStepLog.builder()
                .saga(saga)
                .stepOrder(order)
                .stepName(name)
                .action(action)
                .result(result)
                .durationMs(duration)
                .detail(detail)
                .contextSnapshot(snapshot)
                .build());
    }
}
