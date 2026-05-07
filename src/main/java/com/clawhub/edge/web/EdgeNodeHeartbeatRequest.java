package com.clawhub.edge.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record EdgeNodeHeartbeatRequest(
        @NotBlank String nodeId,
        String hostIp,
        @Min(1) @Max(65535) Integer port,
        String qwenpawVersion,
        List<String> capabilities,
        Map<String, Object> metadata,
        String token
) {
}
