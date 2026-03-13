package com.gitanalytics.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserProfileDto {
    private UUID id;
    private String username;
    private String email;
    private String avatarUrl;
}
