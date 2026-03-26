package com.orchestrator.saga;

import java.util.List;

/**
 * Defines a saga as an ordered list of steps.
 */
public interface SagaDefinition {

    String getType();

    List<SagaStep> getSteps();
}
