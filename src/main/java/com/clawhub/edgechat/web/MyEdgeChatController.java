package com.clawhub.edgechat.web;

import com.clawhub.edgechat.service.EdgeChatService;
import com.clawhub.user.service.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/me/edge-chat")
public class MyEdgeChatController {

    private final EdgeChatService edgeChatService;

    public MyEdgeChatController(EdgeChatService edgeChatService) {
        this.edgeChatService = edgeChatService;
    }

    @GetMapping("/nodes")
    public List<EdgeChatNodeResponse> listNodes(@AuthenticationPrincipal UserPrincipal principal) {
        return edgeChatService.listNodes(principal.getUsername());
    }

    @GetMapping("/sessions")
    public List<EdgeChatSessionResponse> listSessions(@AuthenticationPrincipal UserPrincipal principal) {
        return edgeChatService.listSessions(principal.getId()).stream()
                .map(EdgeChatSessionResponse::from)
                .toList();
    }

    @PostMapping("/sessions")
    public EdgeChatSessionResponse createSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateEdgeChatSessionRequest request
    ) {
        String nodeId = request == null ? null : request.nodeId();
        String title = request == null ? null : request.title();
        String kind = request == null ? null : request.kind();
        return EdgeChatSessionResponse.from(
                edgeChatService.createSession(principal.getId(), principal.getUsername(), nodeId, title, kind)
        );
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<EdgeChatMessageResponse> listMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return edgeChatService.listMessages(principal.getId(), sessionId).stream()
                .map(EdgeChatMessageResponse::from)
                .toList();
    }

    @PostMapping("/stream")
    public SseEmitter stream(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EdgeChatRequest request
    ) {
        return edgeChatService.stream(principal.getId(), principal.getUsername(), request);
    }
}
