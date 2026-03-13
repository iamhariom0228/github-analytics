package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StreakDto {
    private int currentStreak;
    private int longestStreak;
}
