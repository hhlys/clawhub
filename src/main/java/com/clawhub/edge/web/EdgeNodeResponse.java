package com.clawhub.edge.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EdgeNodeResponse(
        Long id,
        String nodeId,
        String tenantId,
        String groupName,
        String username,
        String hostIp,
        Integer port,
        String osName,
        String arch,
        String qwenpawVersion,
        List<String> capabilities,
        Map<String, Object> metadata,
        String status,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt
) {
}
