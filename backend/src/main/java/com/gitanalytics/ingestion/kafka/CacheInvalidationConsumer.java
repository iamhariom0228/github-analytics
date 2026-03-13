package com.gitanalytics.ingestion.kafka;

import com.gitanalytics.shared.kafka.events.SyncCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = "ga.sync.completed", groupId = "github-analytics-cache")
    public void handleSyncCompleted(SyncCompletedEvent event) {
        if (event.getUserId() == null) return;
        String key = "ga:dashboard:" + event.getUserId();
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Cache invalidated for user {}: {}", event.getUserId(), deleted);
    }
}
