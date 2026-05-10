package com.clawhub.instance.service;

import com.clawhub.common.error.CapacityExceededException;
import com.clawhub.common.error.ConflictException;
import com.clawhub.config.ClawProvisioningProperties;
import com.clawhub.config.PlatformLimitsProperties;
import com.clawhub.instance.domain.InstanceStatus;
import com.clawhub.instance.domain.ManagedInstance;
import com.clawhub.instance.domain.ProductType;
import com.clawhub.instance.repo.ManagedInstanceRepository;
import com.clawhub.instance.web.PlatformCapacityResponse;
import com.clawhub.instance.web.ProvisioningProfileResponse;
import com.clawhub.user.domain.AppUser;
import com.clawhub.user.domain.UserStatus;
import com.clawhub.user.service.AppUserService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagedInstanceService {

    private static final String SINGLE_INSTANCE_MESSAGE = "You already have an instance. Delete the current instance before creating a new one.";
    private static final String INSTANCE_LIMIT_MESSAGE = "Instance capacity reached. The platform currently allows up to 5 instances.";

    private final ManagedInstanceRepository repository;
    private final DockerRuntimeService dockerRuntimeService;
    private final AppUserService userService;
    private final PlatformLimitsProperties platformLimitsProperties;
    private final ClawProvisioningProperties provisioningProperties;
    private final QwenPawModelBootstrapService modelBootstrapService;

    public ManagedInstanceService(
            ManagedInstanceRepository repository,
            DockerRuntimeService dockerRuntimeService,
            AppUserService userService,
            PlatformLimitsProperties platformLimitsProperties,
            ClawProvisioningProperties provisioningProperties,
            QwenPawModelBootstrapService modelBootstrapService
    ) {
        this.repository = repository;
        this.dockerRuntimeService = dockerRuntimeService;
        this.userService = userService;
        this.platformLimitsProperties = platformLimitsProperties;
        this.provisioningProperties = provisioningProperties;
        this.modelBootstrapService = modelBootstrapService;
    }

    @Transactional(readOnly = true)
    public List<ManagedInstance> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ManagedInstance> getOwned(Long ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId);
    }

    @Transactional(readOnly = true)
    public PlatformCapacityResponse getCapacity() {
        long current = repository.count();
        int limit = platformLimitsProperties.getInstanceLimit();
        return new PlatformCapacityResponse(limit, current, Math.max(0, limit - current));
    }

    @Transactional(readOnly = true)
    public ProvisioningProfileResponse getProvisioningProfile() {
        List<Integer> usedHostPorts = repository.findAllByOrderByHostPortAsc().stream()
                .map(ManagedInstance::getHostPort)
                .toList();
        return new ProvisioningProfileResponse(
                provisioningProperties.getDefaultHost(),
                provisioningProperties.getContainerPort(),
                findNextAvailableHostPort(),
                usedHostPorts,
                provisioningProperties.getDefaultProductVersion(),
                List.of(provisioningProperties.getDefaultProductVersion())
        );
    }

    @Transactional
    public ManagedInstance createForOwner(
            Long ownerUserId,
            String requestedInstanceName
    ) {
        AppUser ownerUser = validateCreatePreconditions(ownerUserId);
        String instanceName = normalizeInstanceName(requestedInstanceName);
        String resourceName = toDockerSafeName(instanceName, ownerUser.getUsername());
        String containerName = resourceName + "-container";
        int hostPort = findNextAvailableHostPort();
        String host = provisioningProperties.getDefaultHost();
        int containerPort = provisioningProperties.getContainerPort();
        String dockerImage = provisioningProperties.getQwenpawImage();
        String dataVolumeHostPath = provisioningProperties.getDataVolumeHostRootPath() + "/" + resourceName + "-data";
        String dataVolumeContainerPath = provisioningProperties.getDataVolumeContainerPath();
        String publicBaseUrl = "http://" + host + ":" + hostPort;

        validateUniqueFields(instanceName, containerName, hostPort);
        return persistAndOptionallyStart(
                ownerUser,
                ProductType.QWENPAW,
                provisioningProperties.getDefaultProductVersion(),
                instanceName,
                containerName,
                dockerImage,
                host,
                hostPort,
                containerPort,
                dataVolumeHostPath,
                dataVolumeContainerPath,
                publicBaseUrl,
                true
        );
    }

    @Transactional
    public ManagedInstance createForOwner(
            Long ownerUserId,
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
            boolean autoStart
    ) {
        AppUser ownerUser = validateCreatePreconditions(ownerUserId);
        validateUniqueFields(instanceName, containerName, hostPort);
        return persistAndOptionallyStart(
                ownerUser,
                productType,
                productVersion,
                instanceName,
                containerName,
                dockerImage,
                host,
                hostPort,
                containerPort,
                dataVolumeHostPath,
                dataVolumeContainerPath,
                publicBaseUrl,
                autoStart
        );
    }

    @Transactional
    public ManagedInstance start(Long id) {
        ManagedInstance instance = getRequired(id);
        dockerRuntimeService.start(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        return repository.save(instance);
    }

    @Transactional
    public ManagedInstance stop(Long id) {
        ManagedInstance instance = getRequired(id);
        dockerRuntimeService.stop(instance);
        instance.setStatus(InstanceStatus.STOPPED);
        return repository.save(instance);
    }

    @Transactional
    public ManagedInstance restart(Long id) {
        ManagedInstance instance = getRequired(id);
        dockerRuntimeService.restart(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        return repository.save(instance);
    }

    @Transactional
    public void delete(Long id) {
        ManagedInstance instance = getRequired(id);
        dockerRuntimeService.delete(instance);
        repository.delete(instance);
    }

    @Transactional
    public ManagedInstance startOwned(Long ownerUserId) {
        ManagedInstance instance = getRequiredOwned(ownerUserId);
        dockerRuntimeService.start(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        return repository.save(instance);
    }

    @Transactional
    public ManagedInstance stopOwned(Long ownerUserId) {
        ManagedInstance instance = getRequiredOwned(ownerUserId);
        dockerRuntimeService.stop(instance);
        instance.setStatus(InstanceStatus.STOPPED);
        return repository.save(instance);
    }

    @Transactional
    public ManagedInstance restartOwned(Long ownerUserId) {
        ManagedInstance instance = getRequiredOwned(ownerUserId);
        dockerRuntimeService.restart(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        return repository.save(instance);
    }

    @Transactional
    public void deleteOwned(Long ownerUserId) {
        ManagedInstance instance = getRequiredOwned(ownerUserId);
        dockerRuntimeService.delete(instance);
        repository.delete(instance);
    }

    private void validateUniqueFields(String instanceName, String containerName, Integer hostPort) {
        if (repository.existsByInstanceName(instanceName)) {
            throw new ConflictException("instanceName already exists");
        }
        if (repository.existsByContainerName(containerName)) {
            throw new ConflictException("containerName already exists");
        }
        if (repository.existsByHostPort(hostPort)) {
            throw new ConflictException("hostPort already exists");
        }
    }

    private String normalizeInstanceName(String requestedInstanceName) {
        String instanceName = requestedInstanceName == null ? "" : requestedInstanceName.trim();
        if (instanceName.isBlank()) {
            throw new IllegalArgumentException("Instance name is required");
        }
        if (instanceName.length() > 64) {
            throw new IllegalArgumentException("Instance name must be 64 characters or less");
        }
        return instanceName;
    }

    private String toDockerSafeName(String value, String fallback) {
        String fallbackName = fallback == null || fallback.isBlank() ? "claw" : fallback;
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String slug = normalized
                .replaceAll("[^a-z0-9_.-]+", "-")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (slug.isBlank()) {
            slug = fallbackName.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]+", "-")
                    .replaceAll("^[._-]+|[._-]+$", "");
        }
        if (slug.isBlank()) {
            slug = "claw";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("[._-]+$", "");
        }
        return slug;
    }

    private AppUser validateCreatePreconditions(Long ownerUserId) {
        AppUser ownerUser = userService.getRequired(ownerUserId);
        if (ownerUser.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Target user is not active");
        }
        if (repository.existsByOwnerUserId(ownerUserId)) {
            throw new ConflictException(SINGLE_INSTANCE_MESSAGE);
        }
        if (repository.count() >= platformLimitsProperties.getInstanceLimit()) {
            throw new CapacityExceededException(INSTANCE_LIMIT_MESSAGE);
        }
        return ownerUser;
    }

    private ManagedInstance persistAndOptionallyStart(
            AppUser ownerUser,
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
            boolean autoStart
    ) {
        ManagedInstance instance = new ManagedInstance();
        instance.setOwnerUser(ownerUser);
        instance.setProductType(productType);
        instance.setProductVersion(productVersion);
        instance.setInstanceName(instanceName);
        instance.setContainerName(containerName);
        instance.setDockerImage(dockerImage);
        instance.setHost(host);
        instance.setHostPort(hostPort);
        instance.setContainerPort(containerPort);
        instance.setDataVolumeHostPath(dataVolumeHostPath);
        instance.setDataVolumeContainerPath(dataVolumeContainerPath);
        instance.setPublicBaseUrl(publicBaseUrl);
        instance.setStatus(InstanceStatus.PENDING);
        repository.save(instance);
        modelBootstrapService.bootstrap(instance);

        if (autoStart) {
            dockerRuntimeService.createAndStart(instance);
            instance.setStatus(InstanceStatus.RUNNING);
        } else {
            dockerRuntimeService.createAndStart(instance);
            dockerRuntimeService.stop(instance);
            instance.setStatus(InstanceStatus.STOPPED);
        }
        return repository.save(instance);
    }

    private int findNextAvailableHostPort() {
        List<Integer> usedPorts = new ArrayList<>(repository.findAllByOrderByHostPortAsc().stream()
                .map(ManagedInstance::getHostPort)
                .toList());
        int candidate = provisioningProperties.getHostPortStart();
        while (usedPorts.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private ManagedInstance getRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found: " + id));
    }

    private ManagedInstance getRequiredOwned(Long ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found for current user"));
    }
}
