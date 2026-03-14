package com.gitanalytics.ingestion.dto;

public record RepoSuggestionDto(
    Long id,
    String name,
    String fullName,
    boolean privateRepo,
    String owner
) {}
