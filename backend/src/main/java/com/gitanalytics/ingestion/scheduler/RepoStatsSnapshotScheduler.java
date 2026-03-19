package com.gitanalytics.ingestion.scheduler;

import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.repository.RepoStatsSnapshotRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoStatsSnapshotScheduler {

    private final TrackedRepoRepository trackedRepoRepository;
    private final RepoStatsSnapshotRepository snapshotRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void takeSnapshotsOnStartup() {
        takeSnapshots();
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void takeSnapshots() {
        LocalDate today = LocalDate.now();
        List<TrackedRepo> repos = trackedRepoRepository.findAll();
        log.info("Taking stats snapshots for {} repos", repos.size());
        for (TrackedRepo repo : repos) {
            try {
                snapshotRepository.upsert(
                    repo.getId(), today,
                    repo.getStars()    != null ? repo.getStars()    : 0,
                    repo.getForks()    != null ? repo.getForks()    : 0,
                    repo.getWatchers() != null ? repo.getWatchers() : 0
                );
            } catch (Exception e) {
                log.warn("Failed to snapshot repo {}: {}", repo.getFullName(), e.getMessage());
            }
        }
    }
}
