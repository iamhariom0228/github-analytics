package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "issues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Issue {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "issue_number", nullable = false)
    private Integer issueNumber;

    @Column(length = 500)
    private String title;

    @Column(name = "author_login")
    private String authorLogin;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
