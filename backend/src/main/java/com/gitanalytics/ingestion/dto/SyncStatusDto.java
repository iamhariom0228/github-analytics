package com.gitanalytics.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class SyncStatusDto {
    private UUID jobId;
    private String jobStatus;
    private String repoSyncStatus;
}
