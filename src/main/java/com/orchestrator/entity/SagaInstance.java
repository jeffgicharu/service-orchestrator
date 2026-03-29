package com.orchestrator.entity;

import com.orchestrator.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saga_instances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaInstance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sagaId;

    @Column(nullable = false)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    private int currentStep;
    private int totalSteps;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String failureReason;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<SagaStepLog> stepLogs = new ArrayList<>();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() { startedAt = LocalDateTime.now(); }
}
