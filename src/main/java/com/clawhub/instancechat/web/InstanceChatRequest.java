package com.clawhub.instancechat.web;

import jakarta.validation.constraints.NotBlank;

public record InstanceChatRequest(
        @NotBlank String message,
        Long sessionId,
        String conversationId,
        String agentId
) {
}
