package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(nullable = false, length = 40)
    private String sha;

    @Column(name = "author_login")
    private String authorLogin;

    @Column(name = "author_github_id")
    private Long authorGithubId;

    @Column(name = "message_summary", length = 255)
    private String messageSummary;

    private int additions;
    private int deletions;

    @Column(name = "committed_at", nullable = false)
    private OffsetDateTime committedAt;
}
