package com.gitanalytics.shared.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookReceivedEvent {
    private String deliveryId;
    private UUID userId;
    private UUID repoId;
    private String eventType;
    private String payload;
}
