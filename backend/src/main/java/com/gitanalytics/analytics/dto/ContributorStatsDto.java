package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContributorStatsDto {
    private String login;
    private long commits;
    private long linesAdded;
    private long linesRemoved;
}
