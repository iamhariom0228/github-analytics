package com.gitanalytics.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PRLifecycleDto {
    private double avgHoursToFirstReview;
    private double avgHoursToMerge;
    private long mergedCount;
    private long totalCount;
}
