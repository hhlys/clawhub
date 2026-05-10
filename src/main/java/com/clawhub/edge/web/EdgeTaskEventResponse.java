package com.clawhub.edge.web;

import java.time.Instant;
import java.util.Map;

public record EdgeTaskEventResponse(
        Long id,
        String taskId,
        String conversationId,
        Long sequence,
        String eventType,
        String content,
        Map<String, Object> rawEvent,
        Instant createdAt
) {
}
