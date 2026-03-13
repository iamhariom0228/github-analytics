package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pr_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_id", nullable = false)
    private PullRequest pullRequest;

    @Column(name = "reviewer_login", nullable = false)
    private String reviewerLogin;

    @Enumerated(EnumType.STRING)
    private ReviewState state;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    public enum ReviewState {
        APPROVED, CHANGES_REQUESTED, COMMENTED
    }
}
