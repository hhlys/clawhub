package com.clawhub.edgechat.web;

public record CreateEdgeChatSessionRequest(
        String nodeId,
        String title,
        String kind
) {
}
