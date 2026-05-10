package com.clawhub.instancechat.web;

import com.clawhub.instancechat.domain.InstanceChatMessage;
import java.time.Instant;

public record InstanceChatMessageResponse(
        Long id,
        Long sessionId,
        String role,
        String content,
        Long sequenceNo,
        Instant createdAt
) {

    public static InstanceChatMessageResponse from(InstanceChatMessage message) {
        return new InstanceChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getRole(),
                message.getContent(),
                message.getSequenceNo(),
                message.getCreatedAt()
        );
    }
}
