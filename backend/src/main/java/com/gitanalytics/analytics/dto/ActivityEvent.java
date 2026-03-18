package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {
    private String type;
    private String title;
    private String repoFullName;
    private String sha;
    private Integer prNumber;
    private String state;
    private Integer linesAdded;
    private Integer linesRemoved;
    private String occurredAt;
}
