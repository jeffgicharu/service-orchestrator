package com.orchestrator.repository;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {
    Optional<SagaInstance> findBySagaId(String sagaId);
    List<SagaInstance> findByStatusOrderByStartedAtDesc(SagaStatus status);
    List<SagaInstance> findBySagaTypeOrderByStartedAtDesc(String sagaType);
}
