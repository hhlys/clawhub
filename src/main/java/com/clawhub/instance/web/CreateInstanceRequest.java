package com.clawhub.instance.web;

import jakarta.validation.constraints.NotBlank;

public record CreateInstanceRequest(
        @NotBlank String instanceName
) {
}
