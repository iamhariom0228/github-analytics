package com.gitanalytics.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddRepoRequest {
    @NotBlank
    private String owner;
    @NotBlank
    private String name;
}
