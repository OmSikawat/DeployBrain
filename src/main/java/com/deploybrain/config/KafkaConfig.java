package com.deploybrain.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.build-events}")
    private String buildEventsTopicName;

    @Value("${kafka.topic.partitions}")
    private int partitions;

    @Value("${kafka.topic.replication-factor}")
    private short replicationFactor;

    /**
     * Spring Boot's autoconfigured KafkaAdmin bean picks up any NewTopic bean
     * in the context and creates it against the broker before listener
     * containers start polling, so the topic is guaranteed to exist by the
     * time BuildEventConsumer's @KafkaListener subscribes.
     */
    @Bean
    public NewTopic buildEventsTopic() {
        return TopicBuilder.name(buildEventsTopicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * Retries a failing listener invocation up to 3 times with a 2-second
     * gap, then gives up on that specific message and moves on rather than
     * blocking the partition forever on one bad message. Spring Boot's
     * autoconfigured listener container factory automatically picks up this
     * bean since it implements CommonErrorHandler.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);
        return new DefaultErrorHandler(backOff);
    }
}