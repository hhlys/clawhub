package com.clawhub.instance.web;

import java.util.List;

public record ProvisioningProfileResponse(
        String defaultHost,
        int containerPort,
        int nextAvailableHostPort,
        List<Integer> usedHostPorts,
        String defaultProductVersion,
        List<String> supportedProductVersions
) {
}
