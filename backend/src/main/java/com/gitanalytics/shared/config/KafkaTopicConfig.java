package com.gitanalytics.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic syncRequestedTopic() {
        return TopicBuilder.name("ga.sync.requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic syncCompletedTopic() {
        return TopicBuilder.name("ga.sync.completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic webhookReceivedTopic() {
        return TopicBuilder.name("ga.webhook.received").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic digestTriggerTopic() {
        return TopicBuilder.name("ga.digest.trigger").partitions(1).replicas(1).build();
    }
}
