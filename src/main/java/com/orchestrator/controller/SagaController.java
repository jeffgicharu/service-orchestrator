package com.orchestrator.controller;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.entity.SagaStepLog;
import com.orchestrator.saga.SagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sagas")
@RequiredArgsConstructor
@Tag(name = "Saga Orchestrator", description = "Execute and monitor distributed transactions with compensating actions")
public class SagaController {

    private final SagaOrchestrator orchestrator;

    @PostMapping("/execute")
    @Operation(summary = "Execute a saga", description = "Runs all steps; compensates on failure")
    public ResponseEntity<SagaInstance> execute(@RequestParam String sagaType,
                                                @RequestBody Map<String, Object> context) {
        SagaInstance result = orchestrator.execute(sagaType, context);
        HttpStatus status = result.getStatus().name().contains("COMPENS") || result.getStatus().name().equals("FAILED")
                ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga instance details")
    public SagaInstance get(@PathVariable String sagaId) {
        return orchestrator.getById(sagaId);
    }

    @GetMapping("/{sagaId}/logs")
    @Operation(summary = "Get step execution logs")
    public List<SagaStepLog> getLogs(@PathVariable String sagaId) {
        return orchestrator.getLogs(sagaId);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get orchestrator statistics")
    public Map<String, Object> getStats() {
        return orchestrator.getStats();
    }
}
