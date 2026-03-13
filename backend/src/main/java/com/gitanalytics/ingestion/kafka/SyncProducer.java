package com.gitanalytics.ingestion.kafka;

import com.gitanalytics.shared.kafka.events.SyncCompletedEvent;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSyncRequested(SyncRequestedEvent event) {
        log.debug("Publishing SyncRequestedEvent for repo: {}", event.getRepoId());
        kafkaTemplate.send("ga.sync.requested", event.getRepoId().toString(), event);
    }

    public void publishSyncCompleted(SyncCompletedEvent event) {
        log.debug("Publishing SyncCompletedEvent for user: {}", event.getUserId());
        kafkaTemplate.send("ga.sync.completed", event.getUserId().toString(), event);
    }
}
