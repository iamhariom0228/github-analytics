package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pull_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(length = 500)
    private String title;

    @Column(name = "author_login")
    private String authorLogin;

    @Enumerated(EnumType.STRING)
    private PrState state = PrState.OPEN;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "first_review_at")
    private OffsetDateTime firstReviewAt;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    private int additions;
    private int deletions;

    @Column(name = "changed_files")
    private int changedFiles;

    public enum PrState {
        OPEN, CLOSED, MERGED
    }
}
