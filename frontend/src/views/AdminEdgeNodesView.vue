<script setup lang="ts">
import { onMounted, ref, nextTick, watch } from "vue";
import { apiRequest } from "../api";
import type { EdgeNode, EdgeTaskEvent } from "../types";

const nodes = ref<EdgeNode[]>([]);
const error = ref("");
const success = ref("");
const loading = ref(false);

// -- dispatch dialog --
const dispatchDialog = ref(false);
const dispatchNode = ref<EdgeNode | null>(null);
const dispatchMessage = ref("");
const dispatchStreaming = ref(false);
const dispatchResult = ref("");
const dispatchError = ref("");
const dispatchConversationId = ref("");
const conversationEvents = ref<EdgeTaskEvent[]>([]);
const resultPanel = ref<HTMLDivElement | null>(null);

// auto-scroll result panel as text arrives
watch(dispatchResult, async () => {
  await nextTick();
  if (resultPanel.value) {
    resultPanel.value.scrollTop = resultPanel.value.scrollHeight;
  }
});

function formatTime(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function formatEndpoint(node: EdgeNode) {
  if (!node.hostIp && !node.port) return "-";
  return `${node.hostIp || "-"}:${node.port ?? "-"}`;
}

function defaultConversationId(node: EdgeNode) {
  return `edge:${node.nodeId}:default`;
}

function eventDisplayText(event: EdgeTaskEvent) {
  if (event.content) return event.content;
  const raw = event.rawEvent || {};
  if (typeof raw.response === "string") return raw.response;
  if (typeof raw.error === "string") return raw.error;
  return "";
}

async function loadConversationEvents() {
  if (!dispatchConversationId.value) return;
  conversationEvents.value = await apiRequest<EdgeTaskEvent[]>(
    `/api/admin/edge-nodes/conversations/${encodeURIComponent(dispatchConversationId.value)}/events`,
  );
}

async function loadNodes() {
  loading.value = true;
  error.value = "";
  try {
    nodes.value = await apiRequest<EdgeNode[]>("/api/admin/edge-nodes");
    success.value = `Refreshed ${nodes.value.length} edge node(s).`;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load edge nodes";
  } finally {
    loading.value = false;
  }
}

async function openDispatch(node: EdgeNode) {
  dispatchNode.value = node;
  dispatchMessage.value = "";
  dispatchResult.value = "";
  dispatchError.value = "";
  dispatchConversationId.value = defaultConversationId(node);
  conversationEvents.value = [];
  dispatchDialog.value = true;
  try {
    await loadConversationEvents();
  } catch (err) {
    dispatchError.value = err instanceof Error ? err.message : "Failed to load conversation";
  }
}

function closeDispatch() {
  dispatchDialog.value = false;
  dispatchNode.value = null;
}

async function sendDispatch() {
  if (!dispatchNode.value || !dispatchMessage.value.trim()) return;

  dispatchStreaming.value = true;
  dispatchResult.value = "";
  dispatchError.value = "";

  // Track completed message IDs to skip full-text dupes after streaming deltas
  const seenMessageIds = new Set<string>();

  try {
    const response = await fetch(
      `/api/admin/edge-nodes/${dispatchNode.value.nodeId}/dispatch`,
      {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: dispatchMessage.value,
          conversationId: dispatchConversationId.value,
        }),
      },
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `HTTP ${response.status}`);
    }

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        // Spring SseEmitter outputs "data:" (no space); SSE spec uses "data: "
        let raw: string;
        if (line.startsWith("data: ")) {
          raw = line.slice(6);
        } else if (line.startsWith("data:")) {
          raw = line.slice(5);
        } else {
          continue;
        }
        try {
          const event = JSON.parse(raw);

          if (event.error) {
            dispatchError.value = event.error;
            continue;
          }

          // Streaming content delta — the real-time characters
          if (
            event.object === "content" &&
            event.type === "text" &&
            event.text
          ) {
            dispatchResult.value += event.text;
            continue;
          }

          // Completed message — use full text only if we haven't already
          // streamed deltas for this message id
          if (event.object === "message" && event.content) {
            const msgId = event.id;
            if (msgId && seenMessageIds.has(msgId)) continue;
            if (msgId) seenMessageIds.add(msgId);

            for (const part of event.content) {
              if (part.type === "text" && part.text) {
                // If we got streaming content for this, the result already
                // contains the deltas. Only append if no prior content events.
                if (!dispatchResult.value) {
                  dispatchResult.value += part.text;
                }
              }
            }
          }
        } catch {
          // non-JSON data line, skip
        }
      }
    }
  } catch (err) {
    dispatchError.value = err instanceof Error ? err.message : "Dispatch failed";
  } finally {
    dispatchStreaming.value = false;
    try {
      await loadConversationEvents();
    } catch {
      // Best-effort history refresh.
    }
  }
}

onMounted(loadNodes);
</script>

<template>
  <div class="panel">
    <div class="section-head">
      <div>
        <p class="eyebrow">Edge Nodes</p>
        <h2>边侧 QwenPaw 节点</h2>
        <p class="muted">查看客户 Linux 环境中主动注册到龙虾控制台的边侧服务。</p>
      </div>
      <button class="btn btn-primary" :disabled="loading" @click="loadNodes">
        {{ loading ? "刷新中..." : "刷新" }}
      </button>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="success && !error" class="flash flash-success">{{ success }}</div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>节点</th>
            <th>租户 / 群组</th>
            <th>状态</th>
            <th>地址</th>
            <th>环境</th>
            <th>能力</th>
            <th>最后心跳</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.id">
            <td>
              <strong>{{ node.nodeId }}</strong>
              <div class="mono">{{ node.username }}</div>
            </td>
            <td>
              <strong>{{ node.tenantId }}</strong>
              <div class="mono">{{ node.groupName }}</div>
            </td>
            <td>
              <span class="status-pill" :class="`status-${node.status}`">
                {{ node.status }}
              </span>
            </td>
            <td class="mono">{{ formatEndpoint(node) }}</td>
            <td>
              <div>{{ node.osName || "-" }} / {{ node.arch || "-" }}</div>
              <div class="mono">QwenPaw {{ node.qwenpawVersion || "-" }}</div>
            </td>
            <td class="mono">
              {{ (node.capabilities || []).join(", ") || "-" }}
            </td>
            <td>{{ formatTime(node.lastSeenAt) }}</td>
            <td>
              <button
                class="btn btn-soft"
                :disabled="node.status !== 'online'"
                @click="openDispatch(node)"
              >
                下发
              </button>
            </td>
          </tr>
          <tr v-if="!nodes.length && !loading">
            <td colspan="8" class="muted">暂无边侧节点注册。</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Dispatch Dialog -->
    <div v-if="dispatchDialog" class="modal-backdrop" @click.self="closeDispatch">
      <div class="modal-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">意图下发</p>
            <h2 v-if="dispatchNode">
              {{ dispatchNode.nodeId }}
              <span class="muted" style="font-size:14px;font-weight:400">
                {{ formatEndpoint(dispatchNode) }}
              </span>
            </h2>
          </div>
          <button class="btn btn-soft" @click="closeDispatch">关闭</button>
        </div>

        <div class="field-full" style="margin-bottom:14px">
          <label>输入意图，下发给边侧 QwenPaw 执行</label>
          <textarea
            v-model="dispatchMessage"
            style="
              border: 1px solid var(--line);
              border-radius: 14px;
              padding: 12px 14px;
              background: rgba(255,255,255,0.84);
              color: var(--ink);
              resize: vertical;
              min-height: 80px;
              font: inherit;
            "
            placeholder="例如：检查服务器磁盘使用情况，清理 /tmp 下超过 7 天的文件"
            :disabled="dispatchStreaming"
          ></textarea>
        </div>

        <div
          v-if="conversationEvents.length"
          style="
            background: rgba(255,255,255,0.72);
            border: 1px solid var(--line);
            border-radius: 16px;
            padding: 14px;
            margin-bottom: 14px;
            max-height: 220px;
            overflow: auto;
          "
        >
          <div class="muted" style="font-size:13px;margin-bottom:8px">
            Conversation: {{ dispatchConversationId }}
          </div>
          <div
            v-for="event in conversationEvents"
            :key="event.id"
            style="font-size:13px;line-height:1.6;margin-bottom:8px"
          >
            <span class="mono">[{{ event.eventType }}]</span>
            <span class="muted"> {{ formatTime(event.createdAt) }}</span>
            <div v-if="eventDisplayText(event)" style="white-space:pre-wrap">
              {{ eventDisplayText(event) }}
            </div>
          </div>
        </div>

        <div class="btn-row" style="margin-bottom:18px">
          <button
            class="btn btn-primary"
            :disabled="dispatchStreaming || !dispatchMessage.trim()"
            @click="sendDispatch"
          >
            {{ dispatchStreaming ? "执行中..." : "发送" }}
          </button>
          <span v-if="dispatchStreaming" class="muted" style="font-size:13px">
            Agent 正在思考，请稍候...
          </span>
        </div>

        <div v-if="dispatchError" class="flash flash-error">{{ dispatchError }}</div>

        <!-- Response panel — always visible during/after streaming -->
        <div
          v-if="dispatchStreaming || dispatchResult"
          ref="resultPanel"
          style="
            background: rgba(16,88,97,0.06);
            border: 1px solid var(--line);
            border-radius: 16px;
            padding: 18px;
            max-height: 360px;
            overflow: auto;
            white-space: pre-wrap;
            font-family: Consolas, monospace;
            font-size: 13px;
            line-height: 1.7;
          "
        >
          <template v-if="dispatchResult">
            {{ dispatchResult }}
          </template>
          <template v-else>
            <span class="muted">等待边侧 Agent 响应...</span>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
