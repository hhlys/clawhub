package com.clawhub.edge.web;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record EdgeNodeEventRequest(
        @NotBlank String nodeId,
        String conversationId,
        @NotBlank String eventType,
        String content,
        Map<String, Object> rawEvent,
        String token
) {
}
