package com.orchestrator.controller;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.entity.SagaStepLog;
import com.orchestrator.enums.SagaStatus;
import com.orchestrator.saga.SagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    @Operation(summary = "Get step execution logs with context snapshots")
    public List<SagaStepLog> getLogs(@PathVariable String sagaId) {
        return orchestrator.getLogs(sagaId);
    }

    @PostMapping("/{sagaId}/retry")
    @Operation(summary = "Retry a failed or compensated saga")
    public ResponseEntity<SagaInstance> retry(@PathVariable String sagaId) {
        SagaInstance result = orchestrator.retry(sagaId);
        HttpStatus status = result.getStatus() == SagaStatus.COMPLETED ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping
    @Operation(summary = "List all sagas with pagination")
    public Page<SagaInstance> list(@PageableDefault(size = 20) Pageable pageable) {
        return orchestrator.listAll(pageable);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filter sagas by status")
    public Page<SagaInstance> listByStatus(@PathVariable SagaStatus status,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return orchestrator.listByStatus(status, pageable);
    }

    @GetMapping("/type/{sagaType}")
    @Operation(summary = "Filter sagas by type")
    public Page<SagaInstance> listByType(@PathVariable String sagaType,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return orchestrator.listByType(sagaType, pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get orchestrator statistics with per-type breakdown")
    public Map<String, Object> getStats() {
        return orchestrator.getStats();
    }

    @GetMapping("/types")
    @Operation(summary = "List all registered saga types and their steps")
    public List<Map<String, Object>> getTypes() {
        return orchestrator.getRegisteredTypes();
    }
}
