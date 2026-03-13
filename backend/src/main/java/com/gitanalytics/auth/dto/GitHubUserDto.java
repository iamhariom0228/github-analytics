package com.gitanalytics.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubUserDto {
    private Long id;
    private String login;
    private String email;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private String name;
}
