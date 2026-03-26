package com.orchestrator.repository;

import com.orchestrator.entity.SagaStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {
    List<SagaStepLog> findBySagaIdOrderByStepOrder(Long sagaId);
}
