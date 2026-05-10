package com.clawhub.instancechat.service;

import com.clawhub.instance.domain.InstanceStatus;
import com.clawhub.instance.domain.ManagedInstance;
import com.clawhub.instance.service.ManagedInstanceService;
import com.clawhub.instancechat.domain.InstanceChatSession;
import com.clawhub.instancechat.web.InstanceChatRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class InstanceChatProxyService {

    private static final String CLAWHUB_CHAT_PATH = "/api/custom/clawhub-chat/stream";

    private final ManagedInstanceService managedInstanceService;
    private final InstanceChatStoreService instanceChatStoreService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InstanceChatProxyService(
            ManagedInstanceService managedInstanceService,
            InstanceChatStoreService instanceChatStoreService,
            ObjectMapper objectMapper
    ) {
        this.managedInstanceService = managedInstanceService;
        this.instanceChatStoreService = instanceChatStoreService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public SseEmitter streamOwnedInstanceChat(
            Long ownerUserId,
            String username,
            InstanceChatRequest request
    ) {
        ManagedInstance instance = requireRunningOwnedInstance(ownerUserId);
        InstanceChatSession chatSession = instanceChatStoreService.getOrCreateSession(
                instance,
                ownerUserId,
                request.sessionId()
        );
        instanceChatStoreService.appendMessage(chatSession.getId(), "user", request.message(), null);

        SseEmitter emitter = new SseEmitter(300_000L);
        Thread proxyThread = new Thread(
                () -> proxyStream(instance, chatSession, username, request, emitter),
                "instance-chat-proxy-" + instance.getId() + "-" + chatSession.getId()
        );
        proxyThread.setDaemon(true);
        proxyThread.start();
        return emitter;
    }

    public Optional<ManagedInstance> getOwnedInstance(Long ownerUserId) {
        return managedInstanceService.getOwned(ownerUserId);
    }

    public ManagedInstance requireRunningOwnedInstance(Long ownerUserId) {
        ManagedInstance instance = managedInstanceService.getOwned(ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found for current user"));

        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instance is not running");
        }
        return instance;
    }

    private void proxyStream(
            ManagedInstance instance,
            InstanceChatSession chatSession,
            String username,
            InstanceChatRequest request,
            SseEmitter emitter
    ) {
        StringBuilder assistantBuffer = new StringBuilder();
        boolean[] sawContentDelta = {false};
        try {
            String target = resolveEntryUrl(instance) + CLAWHUB_CHAT_PATH;
            String body = objectMapper.writeValueAsString(toProxyPayload(chatSession, username, request));

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofMinutes(5))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                String errorMessage = "QwenPaw chat channel returned HTTP "
                        + response.statusCode() + ": " + errorBody;
                instanceChatStoreService.appendMessage(chatSession.getId(), "assistant", errorMessage, null);
                emitter.send(SseEmitter.event().data(errorJson(errorMessage)));
                emitter.complete();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        captureAssistantText(data, assistantBuffer, sawContentDelta);
                        emitter.send(SseEmitter.event().data(data));
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5);
                        captureAssistantText(data, assistantBuffer, sawContentDelta);
                        emitter.send(SseEmitter.event().data(data));
                    }
                }
            }

            if (!assistantBuffer.isEmpty()) {
                instanceChatStoreService.appendMessage(chatSession.getId(), "assistant", assistantBuffer.toString(), null);
            }
            emitter.complete();
        } catch (Exception e) {
            try {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                instanceChatStoreService.appendMessage(chatSession.getId(), "assistant", message, null);
                emitter.send(SseEmitter.event().data(errorJson(message)));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
        }
    }

    private Map<String, Object> toProxyPayload(
            InstanceChatSession chatSession,
            String username,
            InstanceChatRequest request
    ) {
        String agentId = request.agentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = "default";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", request.message());
        payload.put("conversationId", chatSession.getQwenpawSessionId());
        payload.put("agentId", agentId);
        payload.put("senderId", "clawhub:" + username);
        return payload;
    }

    private String resolveEntryUrl(ManagedInstance instance) {
        String baseUrl = instance.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://" + instance.getHost() + ":" + instance.getHostPort();
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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

    private void captureAssistantText(
            String data,
            StringBuilder assistantBuffer,
            boolean[] sawContentDelta
    ) {
        try {
            Map<String, Object> event = objectMapper.readValue(data, new TypeReference<>() {
            });
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

            if (!sawContentDelta[0] && "message".equals(object)) {
                assistantBuffer.append(text);
            }
        } catch (Exception ignored) {
            // Streaming should keep flowing even if a single event cannot be parsed for persistence.
        }
    }

    @SuppressWarnings("unchecked")
    private String extractEventText(Map<String, Object> event) {
        Object directText = event.get("text");
        if (directText instanceof String text) {
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

        return "";
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
}
