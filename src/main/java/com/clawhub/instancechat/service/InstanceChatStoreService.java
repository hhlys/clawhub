package com.clawhub.instancechat.service;

import com.clawhub.instance.domain.ManagedInstance;
import com.clawhub.instancechat.domain.InstanceChatMessage;
import com.clawhub.instancechat.domain.InstanceChatSession;
import com.clawhub.instancechat.repo.InstanceChatMessageRepository;
import com.clawhub.instancechat.repo.InstanceChatSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstanceChatStoreService {

    private final InstanceChatSessionRepository sessionRepository;
    private final InstanceChatMessageRepository messageRepository;

    public InstanceChatStoreService(
            InstanceChatSessionRepository sessionRepository,
            InstanceChatMessageRepository messageRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<InstanceChatSession> listSessions(Long ownerUserId, Long instanceId) {
        return sessionRepository.findTop30ByOwnerUserIdAndInstanceIdOrderByLastMessageAtDescUpdatedAtDesc(
                ownerUserId,
                instanceId
        );
    }

    @Transactional(readOnly = true)
    public List<InstanceChatMessage> listMessages(Long ownerUserId, Long sessionId) {
        InstanceChatSession session = getOwnedSession(ownerUserId, sessionId);
        return messageRepository.findTop200BySessionIdOrderBySequenceNoAscIdAsc(session.getId());
    }

    @Transactional
    public InstanceChatSession createSession(ManagedInstance instance) {
        InstanceChatSession session = new InstanceChatSession();
        session.setOwnerUser(instance.getOwnerUser());
        session.setInstance(instance);
        session.setQwenpawSessionId(buildQwenpawSessionId(instance));
        session.setTitle("新对话");
        session.setLastMessageAt(Instant.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public InstanceChatSession getOrCreateSession(
            ManagedInstance instance,
            Long ownerUserId,
            Long sessionId
    ) {
        if (sessionId == null) {
            return createSession(instance);
        }

        InstanceChatSession session = getOwnedSession(ownerUserId, sessionId);
        if (!session.getInstance().getId().equals(instance.getId())) {
            throw new EntityNotFoundException("Chat session not found for current instance");
        }
        return session;
    }

    @Transactional
    public InstanceChatSession getOwnedSession(Long ownerUserId, Long sessionId) {
        return sessionRepository.findByIdAndOwnerUserId(sessionId, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found: " + sessionId));
    }

    @Transactional
    public InstanceChatMessage appendMessage(
            Long sessionId,
            String role,
            String content,
            String rawEventJson
    ) {
        InstanceChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found: " + sessionId));

        InstanceChatMessage message = new InstanceChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setRawEventJson(rawEventJson);
        message.setSequenceNo(messageRepository.countBySessionId(sessionId) + 1);

        session.setLastMessageAt(Instant.now());
        if ("user".equals(role) && isUntitled(session.getTitle())) {
            session.setTitle(toTitle(content));
        }
        sessionRepository.save(session);
        return messageRepository.save(message);
    }

    private String buildQwenpawSessionId(ManagedInstance instance) {
        return "clawhub:user:" + instance.getOwnerUser().getId()
                + ":instance:" + instance.getId()
                + ":session:" + UUID.randomUUID();
    }

    private boolean isUntitled(String title) {
        return title == null || title.isBlank() || "新对话".equals(title);
    }

    private String toTitle(String content) {
        if (content == null || content.isBlank()) {
            return "新对话";
        }
        String normalized = content.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24) + "...";
    }
}
