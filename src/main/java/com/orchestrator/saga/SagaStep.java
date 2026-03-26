package com.orchestrator.saga;

import java.util.Map;

/**
 * A single step in a saga. Each step has an execute action
 * and a compensating action that undoes it if a later step fails.
 */
public interface SagaStep {

    String getName();

    /**
     * Execute the forward action.
     * @return result with success/failure and detail
     */
    StepOutcome execute(Map<String, Object> context);

    /**
     * Compensate (undo) this step when a later step fails.
     * @return result of compensation
     */
    StepOutcome compensate(Map<String, Object> context);

    record StepOutcome(boolean success, String detail) {
        public static StepOutcome success(String detail) { return new StepOutcome(true, detail); }
        public static StepOutcome failure(String detail) { return new StepOutcome(false, detail); }
    }
}
