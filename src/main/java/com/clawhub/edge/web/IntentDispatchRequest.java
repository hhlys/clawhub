package com.clawhub.edge.web;

import jakarta.validation.constraints.NotBlank;

public record IntentDispatchRequest(@NotBlank String message) {
}
