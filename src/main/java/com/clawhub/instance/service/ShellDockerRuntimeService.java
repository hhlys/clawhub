package com.clawhub.instance.service;

import com.clawhub.config.DockerProperties;
import com.clawhub.instance.domain.ManagedInstance;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShellDockerRuntimeService implements DockerRuntimeService {

    private final DockerProperties dockerProperties;

    public ShellDockerRuntimeService(DockerProperties dockerProperties) {
        this.dockerProperties = dockerProperties;
    }

    @Override
    public void createAndStart(ManagedInstance instance) {
        runCommand(buildCreateCommand(instance));
        start(instance);
    }

    @Override
    public void start(ManagedInstance instance) {
        runCommand(List.of(dockerProperties.getExecutable(), "start", instance.getContainerName()));
    }

    @Override
    public void stop(ManagedInstance instance) {
        runCommand(List.of(dockerProperties.getExecutable(), "stop", instance.getContainerName()));
    }

    @Override
    public void restart(ManagedInstance instance) {
        runCommand(List.of(dockerProperties.getExecutable(), "restart", instance.getContainerName()));
    }

    @Override
    public void delete(ManagedInstance instance) {
        runCommand(List.of(dockerProperties.getExecutable(), "rm", "-f", instance.getContainerName()));
    }

    private List<String> buildCreateCommand(ManagedInstance instance) {
        List<String> command = new ArrayList<>();
        command.add(dockerProperties.getExecutable());
        command.add("create");
        command.add("--name");
        command.add(instance.getContainerName());
        command.add("-e");
        command.add("QWENPAW_WORKING_DIR=" + QwenPawInstancePaths.workingContainerPath(instance));
        command.add("-e");
        command.add("QWENPAW_SECRET_DIR=" + QwenPawInstancePaths.secretContainerPath(instance));
        command.add("-p");
        command.add(instance.getHostPort() + ":" + instance.getContainerPort());
        command.add("-v");
        command.add(QwenPawInstancePaths.workingHostMount(instance) + ":" + QwenPawInstancePaths.workingContainerPath(instance));
        command.add("-v");
        command.add(QwenPawInstancePaths.secretHostMount(instance) + ":" + QwenPawInstancePaths.secretContainerPath(instance));
        command.add(instance.getDockerImage());
        return command;
    }

    private void runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new DockerRuntimeException("Docker command failed: " + String.join(" ", command) + "\n" + output);
            }
        } catch (IOException e) {
            throw new DockerRuntimeException("Failed to execute docker command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerRuntimeException("Docker command interrupted: " + String.join(" ", command), e);
        }
    }
}
