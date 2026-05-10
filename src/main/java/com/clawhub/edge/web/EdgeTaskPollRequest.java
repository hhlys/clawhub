package com.clawhub.edge.web;

import jakarta.validation.constraints.NotBlank;

public record EdgeTaskPollRequest(
        @NotBlank String nodeId,
        String token
) {
}
