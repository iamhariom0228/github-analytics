package com.gitanalytics.analytics.dto;

public record RepoCompareDto(
    String repoId,
    String fullName,
    int healthScore,
    String healthLabel,
    long totalCommits,
    long totalPRs,
    long mergedPRs,
    double avgMergeTimeHours,
    String topContributor,
    int topContributorPct,
    int stars,
    int forks,
    int openIssues
) {}
