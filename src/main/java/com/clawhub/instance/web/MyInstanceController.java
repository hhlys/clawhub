package com.clawhub.instance.web;

import com.clawhub.instance.service.ManagedInstanceService;
import com.clawhub.user.service.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/instance")
public class MyInstanceController {

    private final ManagedInstanceService managedInstanceService;

    public MyInstanceController(ManagedInstanceService managedInstanceService) {
        this.managedInstanceService = managedInstanceService;
    }

    @GetMapping
    public ResponseEntity<ManagedInstanceResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        return managedInstanceService.getOwned(principal.getId())
                .map(ManagedInstanceResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedInstanceResponse create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateInstanceRequest request) {
        return ManagedInstanceResponse.from(managedInstanceService.createForOwner(
                principal.getId(),
                request.instanceName()
        ));
    }

    @PostMapping("/start")
    public ManagedInstanceResponse start(@AuthenticationPrincipal UserPrincipal principal) {
        return ManagedInstanceResponse.from(managedInstanceService.startOwned(principal.getId()));
    }

    @PostMapping("/stop")
    public ManagedInstanceResponse stop(@AuthenticationPrincipal UserPrincipal principal) {
        return ManagedInstanceResponse.from(managedInstanceService.stopOwned(principal.getId()));
    }

    @PostMapping("/restart")
    public ManagedInstanceResponse restart(@AuthenticationPrincipal UserPrincipal principal) {
        return ManagedInstanceResponse.from(managedInstanceService.restartOwned(principal.getId()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal) {
        managedInstanceService.deleteOwned(principal.getId());
    }
}
