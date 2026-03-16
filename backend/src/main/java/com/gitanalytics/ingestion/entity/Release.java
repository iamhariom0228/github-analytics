package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "releases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Release {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "tag_name", nullable = false)
    private String tagName;

    private String name;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
