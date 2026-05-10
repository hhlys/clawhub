package com.clawhub.edge.web;

import com.clawhub.edge.service.EdgeNodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/edge/tasks")
public class EdgeTaskController {

    private final EdgeNodeService edgeNodeService;

    public EdgeTaskController(EdgeNodeService edgeNodeService) {
        this.edgeNodeService = edgeNodeService;
    }

    @PostMapping("/poll")
    public EdgeTaskPollResponse poll(
            @Valid @RequestBody EdgeTaskPollRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.pollTask(request, token);
    }

    @PostMapping("/events")
    public EdgeTaskEventResponse appendNodeEvent(
            @Valid @RequestBody EdgeNodeEventRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.appendNodeEvent(request, token);
    }

    @PostMapping("/{taskId}/events")
    public EdgeTaskEventResponse appendEvent(
            @PathVariable String taskId,
            @Valid @RequestBody EdgeTaskEventRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.appendTaskEvent(taskId, request, token);
    }

    @PostMapping("/{taskId}/complete")
    public EdgeTaskResponse complete(
            @PathVariable String taskId,
            @Valid @RequestBody EdgeTaskCompleteRequest request,
            @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        return edgeNodeService.completeTask(taskId, request, token);
    }
}
