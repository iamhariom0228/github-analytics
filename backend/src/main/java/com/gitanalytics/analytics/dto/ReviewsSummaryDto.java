package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewsSummaryDto {
    private long totalReviewsGiven;
    private long approved;
    private long changesRequested;
    private long commented;
    private double avgReviewsPerPR;
}
