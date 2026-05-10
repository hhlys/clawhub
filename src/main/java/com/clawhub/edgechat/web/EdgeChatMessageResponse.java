package com.clawhub.edgechat.web;

import com.clawhub.edgechat.domain.EdgeChatMessage;
import java.time.Instant;

public record EdgeChatMessageResponse(
        Long id,
        Long sessionId,
        String role,
        String content,
        Long sequenceNo,
        Instant createdAt
) {

    public static EdgeChatMessageResponse from(EdgeChatMessage message) {
        return new EdgeChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getRole(),
                message.getContent(),
                message.getSequenceNo(),
                message.getCreatedAt()
        );
    }
}
