package com.gitanalytics.digest.entity;

import com.gitanalytics.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "digest_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;
}
