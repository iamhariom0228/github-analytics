package com.gitanalytics.analytics.dto;

import java.util.List;

public record RepoHealthDto(
    int score,
    String label,         // "Healthy" / "At Risk" / "Needs Attention"
    List<SignalDto> signals
) {
    public record SignalDto(String name, boolean passed, String detail) {}
}
