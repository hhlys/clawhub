package com.clawhub.edge.service;

import com.clawhub.edge.config.EdgeProperties;
import com.clawhub.edge.domain.EdgeNode;
import com.clawhub.edge.domain.EdgeTask;
import com.clawhub.edge.domain.EdgeTaskEvent;
import com.clawhub.edge.repo.EdgeNodeRepository;
import com.clawhub.edge.repo.EdgeTaskEventRepository;
import com.clawhub.edge.repo.EdgeTaskRepository;
import com.clawhub.edge.web.EdgeTaskCompleteRequest;
import com.clawhub.edge.web.EdgeTaskEventRequest;
import com.clawhub.edge.web.EdgeTaskEventResponse;
import com.clawhub.edge.web.EdgeTaskPollRequest;
import com.clawhub.edge.web.EdgeTaskPollResponse;
import com.clawhub.edge.web.EdgeTaskResponse;
import com.clawhub.edge.web.EdgeNodeEventRequest;
import com.clawhub.edge.web.EdgeNodeHeartbeatRequest;
import com.clawhub.edge.web.EdgeNodeRegisterRequest;
import com.clawhub.edge.web.EdgeNodeResponse;
import com.clawhub.edge.web.IntentDispatchRequest;
import com.clawhub.edgechat.service.EdgeChatEventIngestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EdgeNodeService {

    private static final Logger log = LoggerFactory.getLogger(EdgeNodeService.class);

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final EdgeNodeRepository edgeNodeRepository;
    private final EdgeTaskRepository edgeTaskRepository;
    private final EdgeTaskEventRepository edgeTaskEventRepository;
    private final EdgeProperties edgeProperties;
    private final ObjectMapper objectMapper;
    private final EdgeChatEventIngestService edgeChatEventIngestService;

    public EdgeNodeService(
            EdgeNodeRepository edgeNodeRepository,
            EdgeTaskRepository edgeTaskRepository,
            EdgeTaskEventRepository edgeTaskEventRepository,
            EdgeProperties edgeProperties,
            ObjectMapper objectMapper,
            EdgeChatEventIngestService edgeChatEventIngestService
    ) {
        this.edgeNodeRepository = edgeNodeRepository;
        this.edgeTaskRepository = edgeTaskRepository;
        this.edgeTaskEventRepository = edgeTaskEventRepository;
        this.edgeProperties = edgeProperties;
        this.objectMapper = objectMapper;
        this.edgeChatEventIngestService = edgeChatEventIngestService;
    }

    @Transactional
    public EdgeNodeResponse register(EdgeNodeRegisterRequest request, String headerToken) {
        validateToken(request.token(), headerToken);
        Instant now = Instant.now();
        EdgeNode node = edgeNodeRepository.findByNodeId(request.nodeId())
                .orElseGet(EdgeNode::new);

        node.setNodeId(request.nodeId());
        node.setTenantId(request.tenantId());
        node.setGroupName(request.groupName());
        node.setUsername(request.username());
        node.setHostIp(request.hostIp());
        node.setPort(request.port());
        node.setOsName(request.osName());
        node.setArch(request.arch());
        node.setQwenpawVersion(request.qwenpawVersion());
        node.setCapabilitiesJson(toJson(
                request.capabilities() == null ? List.of() : request.capabilities()
        ));
        node.setMetadataJson(toJson(
                request.metadata() == null ? Map.of() : request.metadata()
        ));
        node.setLastSeenAt(now);

        return toResponse(edgeNodeRepository.save(node), now);
    }

    @Transactional
    public EdgeNodeResponse heartbeat(EdgeNodeHeartbeatRequest request, String headerToken) {
        validateToken(request.token(), headerToken);
        Instant now = Instant.now();
        EdgeNode node = edgeNodeRepository.findByNodeId(request.nodeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node is not registered"));

        if (request.hostIp() != null && !request.hostIp().isBlank()) {
            node.setHostIp(request.hostIp());
        }
        if (request.port() != null) {
            node.setPort(request.port());
        }
        if (request.qwenpawVersion() != null && !request.qwenpawVersion().isBlank()) {
            node.setQwenpawVersion(request.qwenpawVersion());
        }
        if (request.capabilities() != null) {
            node.setCapabilitiesJson(toJson(request.capabilities()));
        }
        if (request.metadata() != null) {
            node.setMetadataJson(toJson(request.metadata()));
        }
        node.setLastSeenAt(now);

        return toResponse(edgeNodeRepository.save(node), now);
    }

    @Transactional(readOnly = true)
    public List<EdgeNodeResponse> listAll() {
        Instant now = Instant.now();
        return edgeNodeRepository.findAll().stream()
                .map(node -> toResponse(node, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public EdgeNodeResponse getByNodeId(String nodeId) {
        Instant now = Instant.now();
        EdgeNode node = edgeNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node not found"));
        return toResponse(node, now);
    }

    public SseEmitter dispatchIntent(String nodeId, IntentDispatchRequest request) {
        EdgeNode node = edgeNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node not found"));

        EdgeTask task = createTask(node, request);
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread dispatchThread = new Thread(() -> {
            try {
                log.info("Queued edge task {} for node {}", task.getTaskId(), nodeId);
                streamTaskEvents(task.getTaskId(), emitter);
            } catch (Exception e) {
                log.error("Dispatch stream failed for node {}", nodeId, e);
                String errMsg = e.getMessage();
                if (errMsg == null || errMsg.isBlank()) {
                    errMsg = e.getClass().getSimpleName() + " - check ClawHub server logs for details";
                }
                try {
                    emitter.send(SseEmitter.event()
                            .data("{\"error\":\"" + escapeJson(errMsg) + "\"}"));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, "cloud-dispatch-" + nodeId);

        dispatchThread.setDaemon(true);
        dispatchThread.start();

        return emitter;
    }

    @Transactional
    public EdgeTaskPollResponse pollTask(EdgeTaskPollRequest request, String headerToken) {
        validateToken(request.token(), headerToken);
        EdgeNode node = edgeNodeRepository.findByNodeId(request.nodeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node is not registered"));
        node.setLastSeenAt(Instant.now());

        EdgeTask task = edgeTaskRepository
                .findFirstByNodeIdAndStatusOrderByCreatedAtAsc(request.nodeId(), "pending")
                .orElse(null);
        if (task == null) {
            return EdgeTaskPollResponse.empty();
        }

        task.setStatus("running");
        task.setLeasedAt(Instant.now());
        task.setAttempts((task.getAttempts() == null ? 0 : task.getAttempts()) + 1);
        EdgeTask saved = edgeTaskRepository.save(task);

        appendEvent(
                saved.getTaskId(),
                saved.getConversationId(),
                0L,
                "task_started",
                "",
                Map.of(
                        "object", "task",
                        "status", "running",
                        "task_id", saved.getTaskId(),
                        "conversation_id", saved.getConversationId()
                )
        );

        return new EdgeTaskPollResponse(
                saved.getTaskId(),
                saved.getConversationId(),
                saved.getNodeId(),
                saved.getInstruction(),
                saved.getAgentId()
        );
    }

    @Transactional
    public EdgeTaskEventResponse appendTaskEvent(
            String taskId,
            EdgeTaskEventRequest request,
            String headerToken
    ) {
        validateToken(request.token(), headerToken);
        EdgeTask task = getTaskForNode(taskId, request.nodeId());
        EdgeTaskEvent event = appendEvent(
                task.getTaskId(),
                request.conversationId(),
                request.sequence(),
                request.eventType(),
                request.content(),
                request.rawEvent() == null ? Map.of() : request.rawEvent()
        );
        return toEventResponse(event);
    }

    @Transactional
    public EdgeTaskEventResponse appendNodeEvent(EdgeNodeEventRequest request, String headerToken) {
        validateToken(request.token(), headerToken);
        EdgeNode node = edgeNodeRepository.findByNodeId(request.nodeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node is not registered"));
        node.setLastSeenAt(Instant.now());

        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = "edge:" + request.nodeId() + ":events";
        }
        String taskId = "edge-event-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> raw = request.rawEvent() == null
                ? Map.of(
                        "object", "edge_event",
                        "event_type", request.eventType(),
                        "node_id", request.nodeId(),
                        "conversation_id", conversationId,
                        "content", request.content() == null ? "" : request.content()
                )
                : request.rawEvent();

        EdgeTaskEvent event = appendEvent(
                taskId,
                conversationId,
                0L,
                request.eventType(),
                request.content(),
                raw
        );
        edgeChatEventIngestService.appendEdgeEvent(event);
        return toEventResponse(event);
    }

    @Transactional
    public EdgeTaskResponse completeTask(
            String taskId,
            EdgeTaskCompleteRequest request,
            String headerToken
    ) {
        validateToken(request.token(), headerToken);
        EdgeTask task = getTaskForNode(taskId, request.nodeId());
        String status = normalizeTerminalStatus(request.status());
        task.setStatus(status);
        task.setResponse(request.response());
        task.setError(request.error());
        task.setCompletedAt(Instant.now());
        EdgeTask saved = edgeTaskRepository.save(task);

        appendEvent(
                saved.getTaskId(),
                request.conversationId(),
                Long.MAX_VALUE,
                "task_" + status,
                request.response(),
                Map.of(
                        "object", "response",
                        "status", status,
                        "task_id", saved.getTaskId(),
                        "conversation_id", request.conversationId(),
                        "response", request.response() == null ? "" : request.response(),
                        "error", request.error() == null ? "" : request.error()
                )
        );

        return toTaskResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EdgeTaskResponse> listNodeTasks(String nodeId) {
        return edgeTaskRepository.findTop20ByNodeIdOrderByCreatedAtDesc(nodeId)
                .stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EdgeTaskEventResponse> listConversationEvents(String conversationId) {
        return edgeTaskEventRepository.findTop200ByConversationIdOrderByIdAsc(conversationId)
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    protected EdgeTask createTask(EdgeNode node, IntentDispatchRequest request) {
        String taskId = "edge-task-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = "edge:" + node.getNodeId() + ":default";
        }
        String agentId = request.agentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = "default";
        }

        EdgeTask task = new EdgeTask();
        task.setTaskId(taskId);
        task.setConversationId(conversationId);
        task.setNodeId(node.getNodeId());
        task.setTenantId(node.getTenantId());
        task.setInstruction(request.message());
        task.setAgentId(agentId);
        task.setStatus("pending");
        return edgeTaskRepository.save(task);
    }

    private void streamTaskEvents(String taskId, SseEmitter emitter) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        Long lastEventId = 0L;

        while (System.currentTimeMillis() < deadline) {
            List<EdgeTaskEvent> events = edgeTaskEventRepository
                    .findByTaskIdAndIdGreaterThanOrderByIdAsc(taskId, lastEventId);
            for (EdgeTaskEvent event : events) {
                lastEventId = event.getId();
                emitter.send(SseEmitter.event().data(event.getRawJson()));
            }

            EdgeTask task = edgeTaskRepository.findByTaskId(taskId).orElse(null);
            if (task != null && isTerminal(task.getStatus())) {
                emitter.complete();
                return;
            }

            Thread.sleep(300L);
        }

        emitter.send(SseEmitter.event().data("{\"error\":\"Timed out waiting for edge task result\"}"));
        emitter.complete();
    }

    private EdgeTask getTaskForNode(String taskId, String nodeId) {
        EdgeTask task = edgeTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge task not found"));
        if (!task.getNodeId().equals(nodeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Edge task does not belong to node");
        }
        return task;
    }

    private EdgeTaskEvent appendEvent(
            String taskId,
            String conversationId,
            Long sequence,
            String eventType,
            String content,
            Map<String, Object> rawEvent
    ) {
        EdgeTaskEvent event = new EdgeTaskEvent();
        event.setTaskId(taskId);
        event.setConversationId(conversationId);
        event.setSequenceNo(sequence == null ? 0L : sequence);
        event.setEventType(eventType == null || eventType.isBlank() ? "event" : eventType);
        event.setContent(content);
        event.setRawJson(toJson(rawEvent == null ? Map.of() : rawEvent));
        return edgeTaskEventRepository.save(event);
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    private String normalizeTerminalStatus(String status) {
        if ("completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
            return status;
        }
        return "failed";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void validateToken(String bodyToken, String headerToken) {
        String expected = edgeProperties.getRegistrationToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        String provided = headerToken;
        if (provided == null || provided.isBlank()) {
            provided = bodyToken;
        }
        if (!expected.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid edge registration token");
        }
    }

    private EdgeNodeResponse toResponse(EdgeNode node, Instant now) {
        return new EdgeNodeResponse(
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
                node.getLastSeenAt(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }

    private EdgeTaskResponse toTaskResponse(EdgeTask task) {
        return new EdgeTaskResponse(
                task.getId(),
                task.getTaskId(),
                task.getConversationId(),
                task.getNodeId(),
                task.getTenantId(),
                task.getInstruction(),
                task.getAgentId(),
                task.getStatus(),
                task.getAttempts(),
                task.getResponse(),
                task.getError(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt()
        );
    }

    private EdgeTaskEventResponse toEventResponse(EdgeTaskEvent event) {
        return new EdgeTaskEventResponse(
                event.getId(),
                event.getTaskId(),
                event.getConversationId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getContent(),
                readMap(event.getRawJson()),
                event.getCreatedAt()
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize edge node payload", e);
        }
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
}
