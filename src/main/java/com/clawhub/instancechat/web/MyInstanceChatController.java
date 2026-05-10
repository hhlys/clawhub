package com.clawhub.instancechat.web;

import com.clawhub.instancechat.service.InstanceChatProxyService;
import com.clawhub.instancechat.service.InstanceChatStoreService;
import com.clawhub.user.service.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/me/instance/chat")
public class MyInstanceChatController {

    private final InstanceChatProxyService instanceChatProxyService;
    private final InstanceChatStoreService instanceChatStoreService;

    public MyInstanceChatController(
            InstanceChatProxyService instanceChatProxyService,
            InstanceChatStoreService instanceChatStoreService
    ) {
        this.instanceChatProxyService = instanceChatProxyService;
        this.instanceChatStoreService = instanceChatStoreService;
    }

    @GetMapping("/sessions")
    public List<InstanceChatSessionResponse> listSessions(@AuthenticationPrincipal UserPrincipal principal) {
        return instanceChatProxyService.getOwnedInstance(principal.getId())
                .map(instance -> instanceChatStoreService.listSessions(principal.getId(), instance.getId()).stream()
                        .map(InstanceChatSessionResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    @PostMapping("/sessions")
    public InstanceChatSessionResponse createSession(@AuthenticationPrincipal UserPrincipal principal) {
        return InstanceChatSessionResponse.from(
                instanceChatStoreService.createSession(instanceChatProxyService.requireRunningOwnedInstance(principal.getId()))
        );
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<InstanceChatMessageResponse> listMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return instanceChatStoreService.listMessages(principal.getId(), sessionId).stream()
                .map(InstanceChatMessageResponse::from)
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId
    ) {
        instanceChatStoreService.deleteSession(principal.getId(), sessionId);
    }

    @PostMapping("/stream")
    public SseEmitter stream(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InstanceChatRequest request
    ) {
        return instanceChatProxyService.streamOwnedInstanceChat(principal.getId(), principal.getUsername(), request);
    }
}
