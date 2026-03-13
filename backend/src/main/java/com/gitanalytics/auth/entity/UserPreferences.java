package com.gitanalytics.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "digest_enabled")
    private boolean digestEnabled = true;

    @Column(name = "digest_day_of_week")
    private int digestDayOfWeek = 1;

    @Column(name = "digest_hour")
    private int digestHour = 9;

    @Column(name = "timezone")
    private String timezone = "UTC";
}
