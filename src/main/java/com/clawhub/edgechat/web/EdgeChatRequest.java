package com.clawhub.edgechat.web;

import jakarta.validation.constraints.NotBlank;

public record EdgeChatRequest(
        Long sessionId,
        String nodeId,
        @NotBlank String message,
        String displayMessage,
        String agentId
) {
}
