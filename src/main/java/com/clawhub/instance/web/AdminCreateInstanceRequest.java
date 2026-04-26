package com.clawhub.instance.web;

import com.clawhub.instance.domain.ProductType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminCreateInstanceRequest(
        @NotNull Long ownerUserId,
        @NotNull ProductType productType,
        @NotBlank String instanceName,
        @NotBlank String containerName,
        @NotBlank String dockerImage,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer hostPort,
        @NotNull @Min(1) @Max(65535) Integer containerPort,
        @NotBlank String dataVolumeHostPath,
        @NotBlank String dataVolumeContainerPath,
        String publicBaseUrl,
        boolean autoStart
) {
}
