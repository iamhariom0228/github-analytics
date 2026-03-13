package com.gitanalytics.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardSummaryDto {
    private long weeklyCommits;
    private long monthlyPRsMerged;
    private double avgMergeTimeHours;
    private int currentStreak;
    private List<PrSummaryDto> recentPRs;
}
