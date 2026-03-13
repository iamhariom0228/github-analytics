package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HeatmapCellDto {
    private int day;   // 0=Sunday .. 6=Saturday
    private int hour;  // 0-23
    private int count;
}
