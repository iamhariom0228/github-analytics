package com.gitanalytics.analytics.repository;

import com.gitanalytics.analytics.entity.SharedSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedSnapshotRepository extends JpaRepository<SharedSnapshot, UUID> {
    Optional<SharedSnapshot> findByTokenAndExpiresAtAfter(String token, OffsetDateTime now);
}
