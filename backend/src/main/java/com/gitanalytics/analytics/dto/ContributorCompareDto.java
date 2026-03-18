package com.gitanalytics.analytics.dto;

public record ContributorCompareDto(
    String login,
    long totalCommits,
    long linesAdded,
    long linesRemoved,
    long totalPRs,
    long mergedPRs,
    long reviewsGiven
) {}
