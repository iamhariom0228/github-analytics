package com.gitanalytics.ingestion.scheduler;

import com.gitanalytics.ingestion.dao.SyncJobDao;
import com.gitanalytics.ingestion.dao.TrackedRepoDao;
import com.gitanalytics.ingestion.entity.SyncJob;
import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncrementalSyncScheduler {

    private final TrackedRepoDao trackedRepoDao;
    private final SyncJobDao syncJobDao;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void triggerIncrementalSync() {
        log.info("Starting scheduled incremental sync");
        List<TrackedRepo> repos = trackedRepoDao.findAll();

        for (TrackedRepo repo : repos) {
            if (repo.getSyncStatus() == TrackedRepo.SyncStatus.SYNCING) {
                log.debug("Skipping repo {} — sync already in progress", repo.getFullName());
                continue;
            }
            try {
                SyncJob job = syncJobDao.save(SyncJob.builder()
                    .user(repo.getUser())
                    .repo(repo)
                    .jobType(SyncJob.JobType.INCREMENTAL_SYNC)
                    .status(SyncJob.JobStatus.PENDING)
                    .build());
                eventPublisher.publishEvent(new SyncRequestedEvent(
                    job.getId(), repo.getUser().getId(), repo.getId(), "INCREMENTAL_SYNC"));
                log.debug("Triggered incremental sync for repo {}", repo.getFullName());
            } catch (Exception e) {
                log.error("Failed to schedule sync for repo {}: {}", repo.getFullName(), e.getMessage());
            }
        }
    }
}
