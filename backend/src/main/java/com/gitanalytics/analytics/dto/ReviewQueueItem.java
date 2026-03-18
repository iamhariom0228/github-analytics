package com.gitanalytics.analytics.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewQueueItem {
    private Long id;
    private Integer prNumber;
    private String title;
    private String repoFullName;
    private String authorLogin;
    private String createdAt;
    private int additions;
    private int deletions;
    private int changedFiles;
    private long ageHours;
    private String sizeLabel;
}
