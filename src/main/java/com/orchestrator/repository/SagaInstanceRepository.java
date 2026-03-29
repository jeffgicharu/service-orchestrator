package com.orchestrator.repository;

import com.orchestrator.entity.SagaInstance;
import com.orchestrator.enums.SagaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {
    Optional<SagaInstance> findBySagaId(String sagaId);
    Page<SagaInstance> findByStatusOrderByStartedAtDesc(SagaStatus status, Pageable pageable);
    Page<SagaInstance> findBySagaTypeOrderByStartedAtDesc(String sagaType, Pageable pageable);
    Page<SagaInstance> findAllByOrderByStartedAtDesc(Pageable pageable);

    long countByStatus(SagaStatus status);
    long countBySagaType(String sagaType);

    @Query("SELECT s.sagaType, s.status, COUNT(s) FROM SagaInstance s GROUP BY s.sagaType, s.status")
    List<Object[]> countByTypeAndStatus();
}
