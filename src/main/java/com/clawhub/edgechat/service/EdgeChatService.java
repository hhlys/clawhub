package com.clawhub.edgechat.service;

import com.clawhub.edge.config.EdgeProperties;
import com.clawhub.edge.domain.EdgeNode;
import com.clawhub.edge.domain.EdgeTask;
import com.clawhub.edge.domain.EdgeTaskEvent;
import com.clawhub.edge.repo.EdgeNodeRepository;
import com.clawhub.edge.repo.EdgeTaskEventRepository;
import com.clawhub.edge.repo.EdgeTaskRepository;
import com.clawhub.edgechat.domain.EdgeChatMessage;
import com.clawhub.edgechat.domain.EdgeChatSession;
import com.clawhub.edgechat.repo.EdgeChatMessageRepository;
import com.clawhub.edgechat.repo.EdgeChatSessionRepository;
import com.clawhub.edgechat.web.EdgeChatNodeResponse;
import com.clawhub.edgechat.web.EdgeChatRequest;
import com.clawhub.user.domain.AppUser;
import com.clawhub.user.service.AppUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EdgeChatService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AppUserService appUserService;
    private final EdgeNodeRepository edgeNodeRepository;
    private final EdgeTaskRepository edgeTaskRepository;
    private final EdgeTaskEventRepository edgeTaskEventRepository;
    private final EdgeChatSessionRepository sessionRepository;
    private final EdgeChatMessageRepository messageRepository;
    private final EdgeProperties edgeProperties;
    private final ObjectMapper objectMapper;

    public EdgeChatService(
            AppUserService appUserService,
            EdgeNodeRepository edgeNodeRepository,
            EdgeTaskRepository edgeTaskRepository,
            EdgeTaskEventRepository edgeTaskEventRepository,
            EdgeChatSessionRepository sessionRepository,
            EdgeChatMessageRepository messageRepository,
            EdgeProperties edgeProperties,
            ObjectMapper objectMapper
    ) {
        this.appUserService = appUserService;
        this.edgeNodeRepository = edgeNodeRepository;
        this.edgeTaskRepository = edgeTaskRepository;
        this.edgeTaskEventRepository = edgeTaskEventRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.edgeProperties = edgeProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EdgeChatNodeResponse> listNodes(String username) {
        List<EdgeNode> nodes = edgeNodeRepository.findByUsernameOrderByLastSeenAtDesc(username);
        if (nodes.isEmpty()) {
            nodes = edgeNodeRepository.findAllByOrderByLastSeenAtDesc();
        }
        Instant now = Instant.now();
        return nodes.stream().map(node -> toNodeResponse(node, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<EdgeChatSession> listSessions(Long ownerUserId) {
        return sessionRepository.findTop30ByOwnerUserIdOrderByLastMessageAtDescUpdatedAtDesc(ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<EdgeChatMessage> listMessages(Long ownerUserId, Long sessionId) {
        EdgeChatSession session = getOwnedSession(ownerUserId, sessionId);
        return messageRepository.findTop200BySessionIdOrderBySequenceNoAscIdAsc(session.getId());
    }

    @Transactional
    public EdgeChatSession createSession(
            Long ownerUserId,
            String username,
            String nodeId,
            String title,
            String kind
    ) {
        AppUser owner = appUserService.getRequired(ownerUserId);
        EdgeNode node = resolveNode(username, nodeId);
        EdgeChatSession session = new EdgeChatSession();
        session.setOwnerUser(owner);
        session.setEdgeNode(node);
        session.setConversationId(buildConversationId(owner, node));
        session.setTitle(normalizeTitle(title));
        session.setKind(normalizeKind(kind));
        session.setLastMessageAt(Instant.now());
        return sessionRepository.save(session);
    }

    public SseEmitter stream(Long ownerUserId, String username, EdgeChatRequest request) {
        EdgeChatSession session = getOrCreateSession(ownerUserId, username, request.sessionId(), request.nodeId());
        EdgeNode node = session.getEdgeNode();
        if (!"online".equals(resolveStatus(node, Instant.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Edge node is offline");
        }

        appendMessage(session.getId(), "user", displayMessage(request), null);
        EdgeTask task = createTask(session, request);

        SseEmitter emitter = new SseEmitter(180_000L);
        Thread thread = new Thread(
                () -> streamTask(task.getTaskId(), session.getId(), emitter),
                "edge-chat-" + session.getId()
        );
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }

    @Transactional
    protected EdgeChatSession getOrCreateSession(
            Long ownerUserId,
            String username,
            Long sessionId,
            String nodeId
    ) {
        if (sessionId != null) {
            return getOwnedSession(ownerUserId, sessionId);
        }
        return createSession(ownerUserId, username, nodeId, null, "chat");
    }

    @Transactional(readOnly = true)
    protected EdgeChatSession getOwnedSession(Long ownerUserId, Long sessionId) {
        return sessionRepository.findByIdAndOwnerUserId(sessionId, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Edge chat session not found: " + sessionId));
    }

    @Transactional
    protected EdgeTask createTask(EdgeChatSession session, EdgeChatRequest request) {
        String agentId = request.agentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = "default";
        }

        EdgeTask task = new EdgeTask();
        task.setTaskId("edge-chat-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        task.setConversationId(session.getConversationId());
        task.setNodeId(session.getEdgeNode().getNodeId());
        task.setTenantId(session.getEdgeNode().getTenantId());
        task.setInstruction(request.message());
        task.setAgentId(agentId);
        task.setStatus("pending");
        return edgeTaskRepository.save(task);
    }

    @Transactional
    protected EdgeChatMessage appendMessage(
            Long sessionId,
            String role,
            String content,
            String rawEventJson
    ) {
        EdgeChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Edge chat session not found: " + sessionId));

        EdgeChatMessage message = new EdgeChatMessage();
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

    private void streamTask(String taskId, Long sessionId, SseEmitter emitter) {
        long deadline = System.currentTimeMillis() + 180_000L;
        Long lastEventId = 0L;
        StringBuilder assistantBuffer = new StringBuilder();
        boolean[] sawContentDelta = {false};

        try {
            while (System.currentTimeMillis() < deadline) {
                List<EdgeTaskEvent> events = edgeTaskEventRepository
                        .findByTaskIdAndIdGreaterThanOrderByIdAsc(taskId, lastEventId);
                for (EdgeTaskEvent event : events) {
                    lastEventId = event.getId();
                    String rawJson = event.getRawJson();
                    captureAssistantText(rawJson, assistantBuffer, sawContentDelta);
                    emitter.send(SseEmitter.event().data(rawJson));
                }

                EdgeTask task = edgeTaskRepository.findByTaskId(taskId).orElse(null);
                if (task != null && isTerminal(task.getStatus())) {
                    if (!assistantBuffer.isEmpty()) {
                        appendMessage(sessionId, "assistant", assistantBuffer.toString(), null);
                    } else if (task.getError() != null && !task.getError().isBlank()) {
                        appendMessage(sessionId, "assistant", task.getError(), null);
                    }
                    emitter.complete();
                    return;
                }

                Thread.sleep(300L);
            }

            String timeout = "{\"object\":\"error\",\"message\":\"Timed out waiting for edge node result\"}";
            appendMessage(sessionId, "assistant", "等待边侧节点执行结果超时", timeout);
            emitter.send(SseEmitter.event().data(timeout));
            emitter.complete();
        } catch (Exception e) {
            try {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                appendMessage(sessionId, "assistant", message, null);
                emitter.send(SseEmitter.event().data(errorJson(message)));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
        }
    }

    private EdgeNode resolveNode(String username, String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            return edgeNodeRepository.findByNodeId(nodeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node not found"));
        }

        List<EdgeNode> nodes = edgeNodeRepository.findByUsernameOrderByLastSeenAtDesc(username);
        if (nodes.isEmpty()) {
            nodes = edgeNodeRepository.findAllByOrderByLastSeenAtDesc();
        }
        if (nodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No edge node is available");
        }
        return nodes.get(0);
    }

    private EdgeChatNodeResponse toNodeResponse(EdgeNode node, Instant now) {
        return new EdgeChatNodeResponse(
                node.getId(),
                node.getNodeId(),
                node.getTenantId(),
                node.getGroupName(),
                node.getUsername(),
                node.getHostIp(),
                node.getPort(),
                node.getOsName(),
                node.getArch(),
                node.getQwenpawVersion(),
                readList(node.getCapabilitiesJson()),
                readMap(node.getMetadataJson()),
                resolveStatus(node, now),
                node.getLastSeenAt()
        );
    }

    private String resolveStatus(EdgeNode node, Instant now) {
        Duration offlineAfter = edgeProperties.getOfflineAfter();
        if (node.getLastSeenAt() == null) {
            return "offline";
        }
        if (offlineAfter == null || offlineAfter.isNegative() || offlineAfter.isZero()) {
            return "online";
        }
        Duration age = Duration.between(node.getLastSeenAt(), now);
        return age.compareTo(offlineAfter) <= 0 ? "online" : "offline";
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    private String buildConversationId(AppUser owner, EdgeNode node) {
        return "edge-chat:user:" + owner.getId()
                + ":node:" + node.getNodeId()
                + ":session:" + UUID.randomUUID();
    }

    private String displayMessage(EdgeChatRequest request) {
        String displayMessage = request.displayMessage();
        if (displayMessage != null && !displayMessage.isBlank()) {
            return displayMessage;
        }
        return request.message();
    }

    private boolean isUntitled(String title) {
        return title == null || title.isBlank() || "New chat".equals(title) || "新对话".equals(title);
    }

    private String toTitle(String content) {
        if (content == null || content.isBlank()) {
            return "New chat";
        }
        String normalized = content.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24) + "...";
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "New chat";
        }
        return title.strip();
    }

    private String normalizeKind(String kind) {
        if ("task".equals(kind)) {
            return "task";
        }
        return "chat";
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private void captureAssistantText(
            String rawJson,
            StringBuilder assistantBuffer,
            boolean[] sawContentDelta
    ) {
        try {
            Map<String, Object> event = objectMapper.readValue(rawJson, MAP_TYPE);
            Object object = event.get("object");
            String text = extractEventText(event);
            if (text.isBlank()) {
                return;
            }

            if ("content".equals(object)) {
                sawContentDelta[0] = true;
                assistantBuffer.append(text);
                return;
            }

            if (!sawContentDelta[0] && ("message".equals(object) || "response".equals(object))) {
                assistantBuffer.append(text);
            }
        } catch (Exception ignored) {
            // Keep the stream alive even if one event cannot be parsed for persistence.
        }
    }

    private String extractEventText(Map<String, Object> event) {
        Object directText = event.get("text");
        if (directText instanceof String text) {
            return text;
        }

        Object response = event.get("response");
        if (response instanceof String text) {
            return text;
        }

        Object content = event.get("content");
        if (content instanceof List<?> contentParts) {
            return extractTextParts(contentParts);
        }

        Object output = event.get("output");
        if (output instanceof List<?> outputItems) {
            StringBuilder builder = new StringBuilder();
            for (Object outputItem : outputItems) {
                if (outputItem instanceof Map<?, ?> outputMap
                        && outputMap.get("content") instanceof List<?> outputContent) {
                    builder.append(extractTextParts(outputContent));
                }
            }
            return builder.toString();
        }

        Object error = event.get("error");
        return error instanceof String text ? text : "";
    }

    private String extractTextParts(List<?> parts) {
        StringBuilder builder = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> partMap
                    && "text".equals(partMap.get("type"))
                    && partMap.get("text") instanceof String text) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "object", "error",
                    "message", message == null ? "Unknown error" : message
            ));
        } catch (Exception e) {
            return "{\"object\":\"error\",\"message\":\"Unknown error\"}";
        }
    }
}
