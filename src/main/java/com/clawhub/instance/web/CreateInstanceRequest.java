package com.clawhub.instance.web;

import com.clawhub.instance.domain.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInstanceRequest(
        @NotNull ProductType productType,
        @NotBlank String productVersion
) {
}
