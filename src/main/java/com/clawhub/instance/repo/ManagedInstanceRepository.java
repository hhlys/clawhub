package com.clawhub.instance.repo;

import com.clawhub.instance.domain.ManagedInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedInstanceRepository extends JpaRepository<ManagedInstance, Long> {

    boolean existsByInstanceName(String instanceName);

    boolean existsByContainerName(String containerName);

    boolean existsByHostPort(Integer hostPort);

    boolean existsByOwnerUserId(Long ownerUserId);

    Optional<ManagedInstance> findByContainerName(String containerName);

    Optional<ManagedInstance> findByOwnerUserId(Long ownerUserId);

    List<ManagedInstance> findAllByOrderByHostPortAsc();
}
