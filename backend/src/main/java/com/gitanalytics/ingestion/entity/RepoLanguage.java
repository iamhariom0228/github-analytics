package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "repo_languages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepoLanguage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(nullable = false)
    private String language;

    private long bytes;
}
