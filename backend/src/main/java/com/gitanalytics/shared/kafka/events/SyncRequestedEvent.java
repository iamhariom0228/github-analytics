package com.gitanalytics.shared.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequestedEvent {
    private UUID syncJobId;
    private UUID userId;
    private UUID repoId;
    private String syncType; // FULL_SYNC or INCREMENTAL_SYNC
}
