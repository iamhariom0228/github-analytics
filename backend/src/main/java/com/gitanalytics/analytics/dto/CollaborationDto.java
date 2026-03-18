package com.gitanalytics.analytics.dto;

import java.util.List;

public record CollaborationDto(
        List<CollaboratorEntry> topReviewersOfMe,
        List<CollaboratorEntry> topPeopleIReview) {

    public record CollaboratorEntry(String login, long count) {}
}
