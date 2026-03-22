package com.gitanalytics.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncCompletedEvent {
    private UUID syncJobId;
    private UUID userId;
    private List<UUID> repoIds;
    private int recordsProcessed;
}
