package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerCoverageDto {
    private long totalPRs;
    private long reviewedPRs;
    private double coveragePct;
}
