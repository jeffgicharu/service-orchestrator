package com.orchestrator.entity;

import com.orchestrator.enums.StepResult;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_step_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaStepLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private SagaInstance saga;

    private int stepOrder;
    private String stepName;
    private String action;

    @Enumerated(EnumType.STRING)
    private StepResult result;

    private long durationMs;
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String contextSnapshot;

    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() { executedAt = LocalDateTime.now(); }
}
