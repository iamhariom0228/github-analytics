package com.gitanalytics.analytics.dto;

import java.util.List;

public record IssueAnalyticsDto(
    long openCount,
    long closedCount,
    Double avgCloseTimeDays,
    List<AgeBucket> ageDistribution
) {
    public record AgeBucket(String label, long count) {}
}
