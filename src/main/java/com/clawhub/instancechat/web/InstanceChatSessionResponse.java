package com.clawhub.instancechat.web;

import com.clawhub.instancechat.domain.InstanceChatSession;
import java.time.Instant;

public record InstanceChatSessionResponse(
        Long id,
        Long instanceId,
        String qwenpawSessionId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {

    public static InstanceChatSessionResponse from(InstanceChatSession session) {
        return new InstanceChatSessionResponse(
                session.getId(),
                session.getInstance().getId(),
                session.getQwenpawSessionId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getLastMessageAt()
        );
    }
}
