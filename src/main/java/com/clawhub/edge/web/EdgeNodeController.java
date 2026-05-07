package com.clawhub.edge.web;

import com.clawhub.edge.service.EdgeNodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/edge/nodes")
public class EdgeNodeController {

    private final EdgeNodeService edgeNodeService;

    public EdgeNodeController(EdgeNodeService edgeNodeService) {
        this.edgeNodeService = edgeNodeService;
    }

    @PostMapping("/register")
    public EdgeNodeResponse register(
            @Valid @RequestBody EdgeNodeRegisterRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.register(request, token);
    }

    @PostMapping("/heartbeat")
    public EdgeNodeResponse heartbeat(
            @Valid @RequestBody EdgeNodeHeartbeatRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.heartbeat(request, token);
    }
}
