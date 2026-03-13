package com.gitanalytics.ingestion.scheduler;

import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.kafka.SyncProducer;
import com.gitanalytics.ingestion.repository.SyncJobRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncrementalSyncScheduler {

    private final TrackedRepoRepository trackedRepoRepository;
    private final SyncJobRepository syncJobRepository;
    private final SyncProducer syncProducer;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void triggerIncrementalSync() {
        log.info("Starting scheduled incremental sync");
        List<TrackedRepo> repos = trackedRepoRepository.findAll();

        for (TrackedRepo repo : repos) {
            if (repo.getSyncStatus() == TrackedRepo.SyncStatus.SYNCING) {
                log.debug("Skipping repo {} — sync already in progress", repo.getFullName());
                continue;
            }
            try {
                var job = com.gitanalytics.ingestion.entity.SyncJob.builder()
                    .user(repo.getUser())
                    .repo(repo)
                    .jobType(com.gitanalytics.ingestion.entity.SyncJob.JobType.INCREMENTAL_SYNC)
                    .status(com.gitanalytics.ingestion.entity.SyncJob.JobStatus.PENDING)
                    .build();
                job = syncJobRepository.save(job);

                syncProducer.publishSyncRequested(new SyncRequestedEvent(
                    job.getId(), repo.getUser().getId(), repo.getId(), "INCREMENTAL_SYNC"
                ));
                log.debug("Triggered incremental sync for repo {}", repo.getFullName());
            } catch (Exception e) {
                log.error("Failed to schedule sync for repo {}: {}", repo.getFullName(), e.getMessage());
            }
        }
    }
}
