package com.gitanalytics.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "github_id", unique = true, nullable = false)
    private Long githubId;

    @Column(nullable = false)
    private String username;

    private String email;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "access_token_encrypted", nullable = false)
    private String accessTokenEncrypted;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
