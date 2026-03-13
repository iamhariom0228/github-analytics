package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class PrSummaryDto {
    private Long id;
    private Integer prNumber;
    private String title;
    private String state;
    private OffsetDateTime createdAt;
}
