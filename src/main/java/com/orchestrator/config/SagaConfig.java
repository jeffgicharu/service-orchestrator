package com.orchestrator.config;

import com.orchestrator.saga.SagaDefinition;
import com.orchestrator.saga.SagaOrchestrator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SagaConfig {

    private final SagaOrchestrator orchestrator;
    private final List<SagaDefinition> definitions;

    @PostConstruct
    void registerSagas() {
        definitions.forEach(orchestrator::registerSaga);
    }
}
