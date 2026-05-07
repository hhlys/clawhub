package com.clawhub.instance.web;

import com.clawhub.instance.domain.InstanceStatus;
import com.clawhub.instance.domain.ManagedInstance;
import com.clawhub.instance.domain.ProductType;
import java.time.Instant;

public record ManagedInstanceResponse(
        Long id,
        Long ownerUserId,
        String ownerUsername,
        ProductType productType,
        String productVersion,
        String instanceName,
        String containerName,
        String dockerImage,
        String host,
        Integer hostPort,
        Integer containerPort,
        String dataVolumeHostPath,
        String dataVolumeContainerPath,
        String publicBaseUrl,
        String entryUrl,
        InstanceStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ManagedInstanceResponse from(ManagedInstance instance) {
        String entryUrl = instance.getPublicBaseUrl();
        if (entryUrl == null || entryUrl.isBlank()) {
            entryUrl = "http://" + instance.getHost() + ":" + instance.getHostPort();
        }
        return new ManagedInstanceResponse(
                instance.getId(),
                instance.getOwnerUser().getId(),
                instance.getOwnerUser().getUsername(),
                instance.getProductType(),
                instance.getProductVersion(),
                instance.getInstanceName(),
                instance.getContainerName(),
                instance.getDockerImage(),
                instance.getHost(),
                instance.getHostPort(),
                instance.getContainerPort(),
                instance.getDataVolumeHostPath(),
                instance.getDataVolumeContainerPath(),
                instance.getPublicBaseUrl(),
                entryUrl,
                instance.getStatus(),
                instance.getCreatedAt(),
                instance.getUpdatedAt()
        );
    }
}
