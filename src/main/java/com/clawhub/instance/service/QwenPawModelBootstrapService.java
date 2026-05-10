package com.clawhub.instance.service;

import com.clawhub.config.QwenPawModelProperties;
import com.clawhub.instance.domain.ManagedInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QwenPawModelBootstrapService {

    private final QwenPawModelProperties modelProperties;
    private final ObjectMapper objectMapper;

    public QwenPawModelBootstrapService(QwenPawModelProperties modelProperties) {
        this.modelProperties = modelProperties;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void bootstrap(ManagedInstance instance) {
        Path workingDir = QwenPawInstancePaths.workingHostPath(instance);
        Path secretDir = QwenPawInstancePaths.secretHostPath(instance);

        try {
            Files.createDirectories(workingDir);
            Files.createDirectories(secretDir);
            if (modelProperties.getApiKey() == null || modelProperties.getApiKey().isBlank()) {
                return;
            }
            writeDefaultModel(secretDir);
        } catch (IOException e) {
            throw new DockerRuntimeException("Failed to bootstrap QwenPaw model config for " + instance.getInstanceName(), e);
        }
    }

    private void writeDefaultModel(Path secretDir) throws IOException {
        Path providersDir = secretDir.resolve("providers");
        Path builtinDir = providersDir.resolve("builtin");
        Files.createDirectories(builtinDir);

        writeJson(providersDir.resolve("active_model.json"), Map.of(
                "provider_id", modelProperties.getProviderId(),
                "model", modelProperties.getModel()
        ));
        writeJson(builtinDir.resolve(modelProperties.getProviderId() + ".json"), providerConfig());
    }

    private Map<String, Object> providerConfig() {
        return Map.ofEntries(
                Map.entry("id", modelProperties.getProviderId()),
                Map.entry("name", providerName()),
                Map.entry("base_url", providerBaseUrl()),
                Map.entry("api_key", modelProperties.getApiKey()),
                Map.entry("chat_model", "AnthropicChatModel"),
                Map.entry("models", List.of(modelInfo(modelProperties.getModel()))),
                Map.entry("extra_models", List.of()),
                Map.entry("api_key_prefix", ""),
                Map.entry("is_local", false),
                Map.entry("freeze_url", true),
                Map.entry("require_api_key", true),
                Map.entry("is_custom", false),
                Map.entry("support_model_discovery", false),
                Map.entry("support_connection_check", false),
                Map.entry("generate_kwargs", Map.of()),
                Map.entry("meta", Map.of())
        );
    }

    private Map<String, Object> modelInfo(String model) {
        return Map.of(
                "id", model,
                "name", model.replace("-", " "),
                "supports_multimodal", false,
                "supports_image", false,
                "supports_video", false,
                "probe_source", "documentation",
                "is_free", false,
                "generate_kwargs", Map.of()
        );
    }

    private String providerName() {
        if ("minimax-cn".equals(modelProperties.getProviderId())) {
            return "MiniMax (China)";
        }
        if ("minimax".equals(modelProperties.getProviderId())) {
            return "MiniMax (International)";
        }
        return modelProperties.getProviderId();
    }

    private String providerBaseUrl() {
        if ("minimax-cn".equals(modelProperties.getProviderId())) {
            return "https://api.minimaxi.com/anthropic";
        }
        if ("minimax".equals(modelProperties.getProviderId())) {
            return "https://api.minimax.io/anthropic";
        }
        return "";
    }

    private void writeJson(Path path, Object payload) throws IOException {
        objectMapper.writeValue(path.toFile(), payload);
    }
}
