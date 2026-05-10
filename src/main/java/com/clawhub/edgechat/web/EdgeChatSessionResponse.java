package com.clawhub.edgechat.web;

import com.clawhub.edgechat.domain.EdgeChatSession;
import java.time.Instant;

public record EdgeChatSessionResponse(
        Long id,
        String nodeId,
        String nodeName,
        String conversationId,
        String title,
        String kind,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {

    public static EdgeChatSessionResponse from(EdgeChatSession session) {
        return new EdgeChatSessionResponse(
                session.getId(),
                session.getEdgeNode().getNodeId(),
                session.getEdgeNode().getGroupName(),
                session.getConversationId(),
                session.getTitle(),
                session.getKind() == null || session.getKind().isBlank() ? "chat" : session.getKind(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getLastMessageAt()
        );
    }
}
