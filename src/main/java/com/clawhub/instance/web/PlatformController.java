package com.clawhub.instance.web;

import com.clawhub.instance.service.ManagedInstanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final ManagedInstanceService managedInstanceService;

    public PlatformController(ManagedInstanceService managedInstanceService) {
        this.managedInstanceService = managedInstanceService;
    }

    @GetMapping("/capacity")
    public PlatformCapacityResponse capacity() {
        return managedInstanceService.getCapacity();
    }

    @GetMapping("/provisioning")
    public ProvisioningProfileResponse provisioning() {
        return managedInstanceService.getProvisioningProfile();
    }
}
