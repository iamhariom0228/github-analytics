package com.gitanalytics.digest.repository;

import com.gitanalytics.digest.entity.DigestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface DigestLogRepository extends JpaRepository<DigestLog, Long> {
    boolean existsByUserIdAndWeekStart(UUID userId, LocalDate weekStart);
}
