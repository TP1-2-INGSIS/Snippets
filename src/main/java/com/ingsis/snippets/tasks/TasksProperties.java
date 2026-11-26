package com.ingsis.snippets.tasks;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config de los streams y de la tolerancia a fallos (REDIS_STREAM_*, TASK_TIMEOUT). */
@ConfigurationProperties(prefix = "tasks")
public record TasksProperties(
    String stream,
    String resultsStream,
    String resultsGroup,
    Duration timeout,
    int maxRequeues,
    boolean consumerEnabled,
    Duration pollInterval,
    Duration claimIdle,
    Duration claimInterval,
    int maxDeliveries,
    Duration retryBackoff) {}
