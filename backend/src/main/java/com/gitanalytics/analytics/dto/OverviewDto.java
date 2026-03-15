package com.gitanalytics.analytics.dto;

public record OverviewDto(long commits, long prsAuthored, long reviewsGiven,
                          long linesAdded, long linesRemoved) {}
