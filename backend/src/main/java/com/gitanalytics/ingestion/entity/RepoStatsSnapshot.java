package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "repo_stats_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepoStatsSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "snapshotted_on", nullable = false)
    private LocalDate snapshottedOn;

    private int stars;
    private int forks;
    private int watchers;
}
