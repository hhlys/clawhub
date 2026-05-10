package com.clawhub.edgechat.service;

import com.clawhub.edge.domain.EdgeTaskEvent;
import com.clawhub.edgechat.domain.EdgeChatMessage;
import com.clawhub.edgechat.domain.EdgeChatSession;
import com.clawhub.edgechat.repo.EdgeChatMessageRepository;
import com.clawhub.edgechat.repo.EdgeChatSessionRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdgeChatEventIngestService {

    private final EdgeChatSessionRepository sessionRepository;
    private final EdgeChatMessageRepository messageRepository;

    public EdgeChatEventIngestService(
            EdgeChatSessionRepository sessionRepository,
            EdgeChatMessageRepository messageRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void appendEdgeEvent(EdgeTaskEvent event) {
        sessionRepository.findByConversationId(event.getConversationId()).ifPresent(session -> {
            String content = event.getContent();
            if ((content == null || content.isBlank()) && event.getRawJson() != null) {
                content = event.getRawJson();
            }
            if (content == null || content.isBlank()) {
                return;
            }

            EdgeChatMessage message = new EdgeChatMessage();
            message.setSession(session);
            message.setRole("assistant");
            message.setContent(content);
            message.setRawEventJson(event.getRawJson());
            message.setSequenceNo(messageRepository.countBySessionId(session.getId()) + 1);

            session.setLastMessageAt(Instant.now());
            sessionRepository.save(session);
            messageRepository.save(message);
        });
    }
}
