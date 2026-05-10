package com.clawhub.edge.web;

public record EdgeTaskPollResponse(
        String taskId,
        String conversationId,
        String nodeId,
        String instruction,
        String agentId
) {

    public static EdgeTaskPollResponse empty() {
        return new EdgeTaskPollResponse(null, null, null, null, null);
    }
}
