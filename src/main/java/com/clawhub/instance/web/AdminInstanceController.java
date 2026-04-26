package com.clawhub.instance.web;

import com.clawhub.instance.service.ManagedInstanceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/instances")
public class AdminInstanceController {

    private final ManagedInstanceService managedInstanceService;

    public AdminInstanceController(ManagedInstanceService managedInstanceService) {
        this.managedInstanceService = managedInstanceService;
    }

    @GetMapping
    public List<ManagedInstanceResponse> list() {
        return managedInstanceService.listAll().stream()
                .map(ManagedInstanceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedInstanceResponse create(@Valid @RequestBody AdminCreateInstanceRequest request) {
        return ManagedInstanceResponse.from(managedInstanceService.createForOwner(
                request.ownerUserId(),
                request.productType(),
                request.instanceName(),
                request.containerName(),
                request.dockerImage(),
                request.host(),
                request.hostPort(),
                request.containerPort(),
                request.dataVolumeHostPath(),
                request.dataVolumeContainerPath(),
                request.publicBaseUrl(),
                request.autoStart()
        ));
    }

    @PostMapping("/{id}/start")
    public ManagedInstanceResponse start(@PathVariable Long id) {
        return ManagedInstanceResponse.from(managedInstanceService.start(id));
    }

    @PostMapping("/{id}/stop")
    public ManagedInstanceResponse stop(@PathVariable Long id) {
        return ManagedInstanceResponse.from(managedInstanceService.stop(id));
    }

    @PostMapping("/{id}/restart")
    public ManagedInstanceResponse restart(@PathVariable Long id) {
        return ManagedInstanceResponse.from(managedInstanceService.restart(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        managedInstanceService.delete(id);
    }
}
