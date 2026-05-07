package com.clawhub.edge.service;

import com.clawhub.edge.config.EdgeProperties;
import com.clawhub.edge.domain.EdgeNode;
import com.clawhub.edge.repo.EdgeNodeRepository;
import com.clawhub.edge.web.EdgeNodeHeartbeatRequest;
import com.clawhub.edge.web.EdgeNodeRegisterRequest;
import com.clawhub.edge.web.EdgeNodeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final EdgeProperties edgeProperties;
    private final ObjectMapper objectMapper;

    public EdgeNodeService(
            EdgeNodeRepository edgeNodeRepository,
            EdgeProperties edgeProperties,
            ObjectMapper objectMapper
    ) {
        this.edgeNodeRepository = edgeNodeRepository;
        this.edgeProperties = edgeProperties;
        this.objectMapper = objectMapper;
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

    public SseEmitter dispatchIntent(String nodeId, String message) {
        EdgeNode node = edgeNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge node not found"));

        if (node.getHostIp() == null || node.getHostIp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edge node has no host IP");
        }
        if (node.getPort() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edge node has no port");
        }

        String url = "http://" + node.getHostIp() + ":" + node.getPort() + "/api/cloud/chat";
        SseEmitter emitter = new SseEmitter(60_000L);

        Thread dispatchThread = new Thread(() -> {
            try {
                log.info("Dispatching intent to node {} at {}", nodeId, url);

                String jsonBody = objectMapper.writeValueAsString(
                        Map.of("message", message,
                               "user_id", "clawhub-admin",
                               "session_id", "cloud:" + nodeId + ":" + System.currentTimeMillis())
                );

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(60_000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    String errorBody;
                    try (var in = conn.getErrorStream() != null
                            ? conn.getErrorStream()
                            : conn.getInputStream()) {
                        errorBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    log.warn("Dispatch to {} returned status {}: {}", nodeId, status, errorBody);
                    emitter.send(SseEmitter.event()
                            .data("{\"error\":\"Edge returned " + status
                                  + ": " + escapeJson(errorBody) + "\"}"));
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            emitter.send(SseEmitter.event().data(line.substring(6)));
                        }
                    }
                }
                conn.disconnect();
                emitter.complete();

            } catch (Exception e) {
                log.error("Dispatch to node {} at {} failed", nodeId, url, e);
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
