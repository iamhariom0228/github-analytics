package com.gitanalytics.ingestion.entity;

import com.gitanalytics.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracked_repos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "is_private")
    private boolean isPrivate;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "webhook_id")
    private Long webhookId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public enum SyncStatus {
        PENDING, SYNCING, DONE, FAILED
    }
}
