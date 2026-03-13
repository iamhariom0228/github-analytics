package com.gitanalytics.ingestion.entity;

import com.gitanalytics.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private TrackedRepo repo;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "records_processed")
    private int recordsProcessed;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public enum JobType {
        FULL_SYNC, INCREMENTAL_SYNC, WEBHOOK
    }

    public enum JobStatus {
        PENDING, RUNNING, DONE, FAILED
    }
}
