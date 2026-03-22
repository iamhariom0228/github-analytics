package com.gitanalytics.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DigestTriggerEvent {
    private UUID userId;
    private LocalDate weekStart;
}
