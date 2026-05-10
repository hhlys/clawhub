package com.clawhub.edge.web;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record EdgeTaskCompleteRequest(
        @NotBlank String nodeId,
        @NotBlank String conversationId,
        @NotBlank String status,
        String response,
        String error,
        Map<String, Object> rawResult,
        String token
) {
}
