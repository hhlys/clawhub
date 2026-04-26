package com.clawhub.instance.web;

public record PlatformCapacityResponse(
        int instanceLimit,
        long currentInstanceCount,
        long remainingInstanceSlots
) {
}
