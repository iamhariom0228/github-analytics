package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class PRSizeDistributionDto {
    private Map<String, Long> buckets; // XS/S/M/L/XL -> count
}
