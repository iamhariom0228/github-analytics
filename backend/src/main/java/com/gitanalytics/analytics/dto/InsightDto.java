package com.gitanalytics.analytics.dto;

public record InsightDto(String message, String type, String metric, String metricLabel) {
    // type: "positive" | "warning" | "info"
    // metric: prominent number/value to display (e.g. "13:00", "100%", "+45%")
    // metricLabel: short label for the metric (e.g. "Peak Time", "Weekday", "vs prev 30d")

    public InsightDto(String message, String type) {
        this(message, type, null, null);
    }
}
