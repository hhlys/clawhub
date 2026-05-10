package com.clawhub.edge.web;

import com.clawhub.edge.service.EdgeNodeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/edge-nodes")
public class AdminEdgeNodeController {

    private final EdgeNodeService edgeNodeService;

    public AdminEdgeNodeController(EdgeNodeService edgeNodeService) {
        this.edgeNodeService = edgeNodeService;
    }

    @GetMapping
    public List<EdgeNodeResponse> list() {
        return edgeNodeService.listAll();
    }

    @GetMapping("/{nodeId}")
    public EdgeNodeResponse detail(@PathVariable String nodeId) {
        return edgeNodeService.getByNodeId(nodeId);
    }

    @PostMapping("/{nodeId}/dispatch")
    public SseEmitter dispatch(@PathVariable String nodeId,
                               @Valid @RequestBody IntentDispatchRequest request) {
        return edgeNodeService.dispatchIntent(nodeId, request);
    }

    @GetMapping("/{nodeId}/tasks")
    public List<EdgeTaskResponse> tasks(@PathVariable String nodeId) {
        return edgeNodeService.listNodeTasks(nodeId);
    }

    @GetMapping("/conversations/{conversationId}/events")
    public List<EdgeTaskEventResponse> conversationEvents(@PathVariable String conversationId) {
        return edgeNodeService.listConversationEvents(conversationId);
    }
}
