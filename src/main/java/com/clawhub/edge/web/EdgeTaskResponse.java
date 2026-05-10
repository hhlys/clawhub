package com.clawhub.edge.web;

import java.time.Instant;

public record EdgeTaskResponse(
        Long id,
        String taskId,
        String conversationId,
        String nodeId,
        String tenantId,
        String instruction,
        String agentId,
        String status,
        Integer attempts,
        String response,
        String error,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
