package com.clawhub.edge.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EdgeTaskEventRequest(
        @NotBlank String nodeId,
        @NotBlank String conversationId,
        @NotNull Long sequence,
        @NotBlank String eventType,
        String content,
        Map<String, Object> rawEvent,
        String token
) {
}
