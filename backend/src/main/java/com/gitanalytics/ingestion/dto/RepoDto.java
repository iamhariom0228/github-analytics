package com.gitanalytics.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class RepoDto {
    private UUID id;
    private String owner;
    private String name;
    private String fullName;
    private boolean isPrivate;
    private String syncStatus;
    private OffsetDateTime lastSyncedAt;
}
