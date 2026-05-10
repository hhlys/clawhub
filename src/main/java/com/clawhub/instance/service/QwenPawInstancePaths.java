package com.clawhub.instance.service;

import com.clawhub.instance.domain.ManagedInstance;
import java.nio.file.Path;

final class QwenPawInstancePaths {

    private QwenPawInstancePaths() {
    }

    static Path workingHostPath(ManagedInstance instance) {
        return resolveHostPath(instance.getDataVolumeHostPath());
    }

    static Path secretHostPath(ManagedInstance instance) {
        return resolveHostPath(secretHostRawPath(instance));
    }

    static String workingHostMount(ManagedInstance instance) {
        return toDockerMountPath(workingHostPath(instance));
    }

    static String secretHostMount(ManagedInstance instance) {
        return toDockerMountPath(secretHostPath(instance));
    }

    private static String secretHostRawPath(ManagedInstance instance) {
        String workingPath = instance.getDataVolumeHostPath();
        if (workingPath.endsWith("-data")) {
            return workingPath.substring(0, workingPath.length() - "-data".length()) + "-secret";
        }
        return workingPath + ".secret";
    }

    static String workingContainerPath(ManagedInstance instance) {
        return instance.getDataVolumeContainerPath();
    }

    static String secretContainerPath(ManagedInstance instance) {
        String workingPath = instance.getDataVolumeContainerPath();
        if (workingPath.endsWith(".qwenpaw")) {
            return workingPath + ".secret";
        }
        return workingPath + ".secret";
    }

    private static Path resolveHostPath(String rawPath) {
        return Path.of(rawPath).toAbsolutePath().normalize();
    }

    private static String toDockerMountPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
