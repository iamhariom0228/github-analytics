package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BusFactorDto {
    private String topContributor;
    private double topContributorPercentage;
    private int totalContributors;
}
