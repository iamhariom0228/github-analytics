package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PRMergeRateDto {
    private String week;
    private long total;
    private long merged;
    private double mergeRate;
}
