package com.gitanalytics.analytics.dto;

import java.util.Map;

public record PublicRepoStatsDto(
    String fullName,
    String description,
    long stars,
    long forks,
    long openIssues,
    String primaryLanguage,
    Map<String, Long> languages,
    int repoHealth,
    String repoHealthLabel,
    long totalCommits,
    long totalPRs,
    long mergedPRs,
    double avgMergeTimeHours,
    String topContributor,
    int topContributorPct,
    boolean isTracked
) {}
