package com.clawhub.instance.service;

import com.clawhub.common.error.CapacityExceededException;
import com.clawhub.common.error.ConflictException;
import com.clawhub.config.PlatformLimitsProperties;
import com.clawhub.instance.domain.InstanceStatus;
import com.clawhub.instance.domain.ManagedInstance;
import com.clawhub.instance.domain.ProductType;
import com.clawhub.instance.repo.ManagedInstanceRepository;
import com.clawhub.instance.web.PlatformCapacityResponse;
import com.clawhub.user.domain.AppUser;
import com.clawhub.user.domain.UserStatus;
import com.clawhub.user.service.AppUserService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
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

    public ManagedInstanceService(
            ManagedInstanceRepository repository,
            DockerRuntimeService dockerRuntimeService,
            AppUserService userService,
            PlatformLimitsProperties platformLimitsProperties
    ) {
        this.repository = repository;
        this.dockerRuntimeService = dockerRuntimeService;
        this.userService = userService;
        this.platformLimitsProperties = platformLimitsProperties;
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

    @Transactional
    public ManagedInstance createForOwner(
            Long ownerUserId,
            ProductType productType,
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
        validateUniqueFields(instanceName, containerName, hostPort);

        ManagedInstance instance = new ManagedInstance();
        instance.setOwnerUser(ownerUser);
        instance.setProductType(productType);
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

    private ManagedInstance getRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found: " + id));
    }

    private ManagedInstance getRequiredOwned(Long ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found for current user"));
    }
}
