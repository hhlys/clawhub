<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { apiRequest } from "../api";
import type { EdgeChatMessage, EdgeChatNode, EdgeChatSession } from "../types";

type ChatRole = "user" | "assistant";
type Frequency = "daily" | "weekly" | "interval" | "once";
type IntervalUnit = "minutes" | "hours" | "days";

interface ChatMessage {
  id: number;
  role: ChatRole;
  content: string;
  createdAt: string;
}

const nodes = ref<EdgeChatNode[]>([]);
const sessions = ref<EdgeChatSession[]>([]);
const activeSession = ref<EdgeChatSession | null>(null);
const selectedNodeId = ref("");
const messages = ref<ChatMessage[]>([]);
const loading = ref(true);
const streaming = ref(false);
const error = ref("");
const draft = ref("");
let refreshTimer: number | undefined;

const taskModalOpen = ref(false);
const taskName = ref("");
const taskText = ref("");
const taskFrequency = ref<Frequency>("interval");
const taskTime = ref("09:00");
const intervalValue = ref(1);
const intervalUnit = ref<IntervalUnit>("hours");

const selectedNode = computed(() =>
  nodes.value.find((node) => node.nodeId === selectedNodeId.value) ?? null
);

async function loadPage() {
  loading.value = true;
  error.value = "";
  try {
    await Promise.all([loadNodes(), loadSessions(true)]);
  } catch (err) {
    error.value = err instanceof Error ? err.message : "加载云边对话失败";
  } finally {
    loading.value = false;
  }
}

async function loadNodes() {
  nodes.value = await apiRequest<EdgeChatNode[]>("/api/me/edge-chat/nodes");
  const onlineNode = nodes.value.find((node) => node.status === "online");
  selectedNodeId.value = onlineNode?.nodeId ?? nodes.value[0]?.nodeId ?? "";
}

async function loadSessions(selectFirst = false) {
  sessions.value = await apiRequest<EdgeChatSession[]>("/api/me/edge-chat/sessions");
  if (selectFirst && sessions.value.length > 0) {
    await selectSession(sessions.value[0]);
  }
}

async function createSession(options: { title?: string; kind?: "chat" | "task" } = {}) {
  if (streaming.value || !selectedNodeId.value) {
    return null;
  }
  const session = await apiRequest<EdgeChatSession>("/api/me/edge-chat/sessions", {
    method: "POST",
    bodyJson: {
      nodeId: selectedNodeId.value,
      title: options.title,
      kind: options.kind ?? "chat"
    }
  });
  activeSession.value = session;
  messages.value = [];
  sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)];
  return session;
}

async function selectSession(session: EdgeChatSession) {
  if (streaming.value) {
    return;
  }
  activeSession.value = session;
  selectedNodeId.value = session.nodeId;
  await refreshActiveMessages();
}

async function refreshActiveMessages() {
  if (!activeSession.value || streaming.value) {
    return;
  }
  const sessionId = activeSession.value.id;
  const storedMessages = await apiRequest<EdgeChatMessage[]>(
    `/api/me/edge-chat/sessions/${sessionId}/messages`
  );
  messages.value = storedMessages
    .filter((message) => message.role === "user" || message.role === "assistant")
    .map((message) => ({
      id: message.id,
      role: message.role as ChatRole,
      content: message.content,
      createdAt: message.createdAt
    }));
}

async function ensureSession() {
  if (activeSession.value && activeSession.value.nodeId === selectedNodeId.value) {
    return activeSession.value;
  }
  return createSession();
}

function appendAssistantText(messageId: number, text: string) {
  if (!text) {
    return;
  }
  const message = messages.value.find((item) => item.id === messageId);
  if (message) {
    message.content += text;
  }
}

function extractTextParts(parts: unknown[]) {
  return parts
    .map((part) => {
      if (
        part &&
        typeof part === "object" &&
        "type" in part &&
        "text" in part &&
        (part as { type?: unknown }).type === "text"
      ) {
        const text = (part as { text?: unknown }).text;
        return typeof text === "string" ? text : "";
      }
      return "";
    })
    .join("");
}

function extractEventText(event: Record<string, unknown>) {
  const directText = event.text;
  if (typeof directText === "string" && directText) {
    return directText;
  }

  const response = event.response;
  if (typeof response === "string" && response) {
    return response;
  }

  const content = event.content;
  if (Array.isArray(content)) {
    return extractTextParts(content);
  }

  const output = event.output;
  if (Array.isArray(output)) {
    return output
      .flatMap((item) => {
        if (!item || typeof item !== "object" || !("content" in item)) {
          return [];
        }
        const itemContent = (item as { content?: unknown }).content;
        return Array.isArray(itemContent) ? itemContent : [];
      })
      .map((part) => {
        if (
          part &&
          typeof part === "object" &&
          "type" in part &&
          "text" in part &&
          (part as { type?: unknown }).type === "text"
        ) {
          const text = (part as { text?: unknown }).text;
          return typeof text === "string" ? text : "";
        }
        return "";
      })
      .join("");
  }

  return "";
}

function captureAssistantText(
  event: Record<string, unknown>,
  assistantMessageId: number,
  state: { sawContentDelta: boolean }
) {
  const textDelta = extractEventText(event);
  if (!textDelta) {
    return;
  }

  if (event.object === "content") {
    state.sawContentDelta = true;
    appendAssistantText(assistantMessageId, textDelta);
    return;
  }

  if (!state.sawContentDelta && (event.object === "message" || event.object === "response")) {
    appendAssistantText(assistantMessageId, textDelta);
  }
}

async function postEdgeMessage(message: string, displayMessage?: string) {
  if (streaming.value) {
    return;
  }

  const session = await ensureSession();
  if (!session) {
    error.value = "请先选择一个在线边侧节点";
    return;
  }

  error.value = "";
  messages.value.push({
    id: Date.now(),
    role: "user",
    content: displayMessage || message,
    createdAt: new Date().toISOString()
  });

  const assistantMessageId = Date.now() + 1;
  messages.value.push({
    id: assistantMessageId,
    role: "assistant",
    content: "",
    createdAt: new Date().toISOString()
  });

  streaming.value = true;
  const captureState = { sawContentDelta: false };
  try {
    const response = await fetch("/api/me/edge-chat/stream", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: session.id,
        nodeId: session.nodeId,
        message,
        displayMessage,
        agentId: "default"
      })
    });

    if (!response.ok || !response.body) {
      const body = await response.text();
      throw new Error(body || `HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        let raw = "";
        if (line.startsWith("data: ")) {
          raw = line.slice(6);
        } else if (line.startsWith("data:")) {
          raw = line.slice(5);
        }
        if (!raw) {
          continue;
        }

        const event = JSON.parse(raw) as Record<string, unknown>;
        if (event.object === "error" || event.error) {
          throw new Error(String(event.message || event.error || "边侧执行失败"));
        }
        captureAssistantText(event, assistantMessageId, captureState);
      }
    }

    const assistant = messages.value.find((item) => item.id === assistantMessageId);
    if (assistant && !assistant.content.trim()) {
      assistant.content = "边侧任务已完成，但没有返回文本内容。";
    }
    await loadSessions(false);
    const refreshed = sessions.value.find((item) => item.id === session.id);
    if (refreshed) {
      activeSession.value = refreshed;
    }
  } catch (err) {
    const assistant = messages.value.find((item) => item.id === assistantMessageId);
    const messageText = err instanceof Error ? err.message : "发送失败";
    if (assistant) {
      assistant.content = `云边对话失败：${messageText}`;
    }
    error.value = messageText;
  } finally {
    streaming.value = false;
  }
}

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || streaming.value) {
    return;
  }
  draft.value = "";
  await postEdgeMessage(text);
}

function setTaskTemplate(text: string) {
  taskText.value = text;
  if (!taskName.value.trim()) {
    taskName.value = text.length <= 18 ? text : text.slice(0, 18);
  }
}

function timeParts() {
  const [hourText, minuteText] = taskTime.value.split(":");
  const hour = Number.parseInt(hourText || "9", 10);
  const minute = Number.parseInt(minuteText || "0", 10);
  return {
    hour: Number.isFinite(hour) ? Math.min(Math.max(hour, 0), 23) : 9,
    minute: Number.isFinite(minute) ? Math.min(Math.max(minute, 0), 59) : 0
  };
}

function cronExpression() {
  const { hour, minute } = timeParts();
  const value = Math.max(1, Math.floor(intervalValue.value || 1));

  if (taskFrequency.value === "daily") {
    return `${minute} ${hour} * * *`;
  }
  if (taskFrequency.value === "weekly") {
    return `${minute} ${hour} * * 1`;
  }
  if (taskFrequency.value === "interval") {
    if (intervalUnit.value === "minutes") {
      return `*/${value} * * * *`;
    }
    if (intervalUnit.value === "hours") {
      return `0 */${value} * * *`;
    }
    return `0 ${hour} */${value} * *`;
  }
  return "";
}

function frequencyLabel() {
  if (taskFrequency.value === "daily") {
    return `每天 ${taskTime.value}`;
  }
  if (taskFrequency.value === "weekly") {
    return `每周一 ${taskTime.value}`;
  }
  if (taskFrequency.value === "interval") {
    const unitMap: Record<IntervalUnit, string> = {
      minutes: "分钟",
      hours: "小时",
      days: "天"
    };
    return `每隔 ${Math.max(1, Math.floor(intervalValue.value || 1))} ${unitMap[intervalUnit.value]}`;
  }
  return "只执行一次";
}

function buildCronInstruction(session: EdgeChatSession) {
  const name = taskName.value.trim() || "边侧定时任务";
  const content = taskText.value.trim();
  const nodePort = selectedNode.value?.port || 8088;
  const localBaseUrl = `http://127.0.0.1:${nodePort}`;

  if (taskFrequency.value === "once") {
    return `请在边侧 QwenPaw 立即执行一次下面的任务，不要创建定时任务。\n\n任务名称：${name}\n任务内容：${content}`;
  }

  const cron = cronExpression();
  return `请在边侧 QwenPaw 创建一个原生内置定时任务。要求如下：

1. 必须使用 QwenPaw 原生 cron 能力创建任务，创建后应能在边侧 QwenPaw 控制台“定时任务”页面看到。
2. 不要自己创建后台脚本、while 循环、外部计划任务或单独 Python 进程。
3. 定时任务执行结果需要通过 cloud_edge channel 回传到当前云边对话。

任务名称：${name}
执行频率：${frequencyLabel()}
Cron 表达式：${cron}
执行内容：${content}

推荐命令如下，请按边侧实际环境执行：

\`\`\`bash
qwenpaw cron create \\
  --base-url ${localBaseUrl} \\
  --agent-id default \\
  --type agent \\
  --name "${name.replaceAll('"', '\\"')}" \\
  --cron "${cron}" \\
  --channel cloud_edge \\
  --target-user "clawhub" \\
  --target-session "${session.conversationId}" \\
  --text "${content.replaceAll('"', '\\"')}"
\`\`\`

创建完成后，请回复创建结果、任务名称、Cron 表达式。`;
}

async function submitTask() {
  const content = taskText.value.trim();
  if (!content) {
    error.value = "请输入执行消息内容";
    return;
  }
  const taskTitle = taskName.value.trim() || "边侧定时任务";
  const session = await createSession({
    title: taskTitle,
    kind: "task"
  });
  if (!session) {
    error.value = "请先选择一个在线边侧节点";
    return;
  }
  const instruction = buildCronInstruction(session);
  const display = `创建边侧定时任务：${taskTitle}\n频率：${frequencyLabel()}\n内容：${content}`;
  taskModalOpen.value = false;
  await postEdgeMessage(instruction, display);
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

onMounted(() => {
  loadPage();
  refreshTimer = window.setInterval(() => {
    refreshActiveMessages();
    loadSessions(false);
  }, 5000);
});

onUnmounted(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer);
  }
});
</script>

<template>
  <section class="chat-layout">
    <aside class="conversation-panel">
      <div class="bot-profile">
        <div class="bot-avatar edge-avatar">E</div>
        <div>
          <strong>云边对话</strong>
          <span>{{ selectedNode?.status === "online" ? "边侧在线" : "等待边侧" }}</span>
        </div>
      </div>

      <label class="edge-node-picker">
        <span>边侧节点</span>
        <select v-model="selectedNodeId" :disabled="streaming">
          <option value="" disabled>请选择边侧节点</option>
          <option v-for="node in nodes" :key="node.nodeId" :value="node.nodeId">
            {{ node.groupName || node.nodeId }} · {{ node.status === "online" ? "在线" : "离线" }}
          </option>
        </select>
      </label>

      <button class="new-chat-button" :disabled="!selectedNodeId || streaming" @click="createSession">
        新云边对话
      </button>

      <div class="side-menu">
        <button class="side-menu-button" :disabled="!selectedNodeId || streaming" @click="taskModalOpen = true">
          定时任务
        </button>
      </div>

      <div class="recent-list">
        <p>最近对话</p>
        <button
          v-for="session in sessions"
          :key="session.id"
          :class="['recent-item', { active: activeSession?.id === session.id }]"
          :disabled="streaming"
          @click="selectSession(session)"
        >
          <strong>
            {{ session.title || "新对话" }}
            <span v-if="session.kind === 'task'" class="session-badge">任务</span>
          </strong>
          <small>{{ session.nodeName || session.nodeId }} · {{ formatTime(session.lastMessageAt) }}</small>
        </button>
        <span v-if="!sessions.length && !loading" class="recent-empty">
          暂无云边对话
        </span>
      </div>
    </aside>

    <section class="chat-main embedded">
      <div class="chat-topline">
        <div>
          <strong>云边对话</strong>
          <span v-if="selectedNode" class="instance-chip">
            {{ selectedNode.groupName || selectedNode.nodeId }} / {{ selectedNode.nodeId }}
          </span>
        </div>
      </div>

      <div v-if="loading" class="empty-chat">
        <h2>正在加载云边通道...</h2>
        <p>ClawHub 正在读取边侧节点和最近对话。</p>
      </div>

      <div v-else-if="!nodes.length" class="empty-chat">
        <h2>还没有可用边侧节点</h2>
        <p>请先让边侧 QwenPaw 通过 cloud_edge custom channel 注册到 ClawHub。</p>
      </div>

      <div v-else class="dialog-stage">
        <div class="dialog-feed">
          <div v-if="!messages.length" class="empty-chat compact">
            <h2>{{ activeSession ? "这是一段云边新对话" : "开始一段云边对话" }}</h2>
            <p>普通对话不会创建任务；需要周期执行时，请使用左侧“定时任务”。</p>
          </div>

          <article
            v-for="message in messages"
            :key="message.id"
            :class="['chat-bubble-row', message.role]"
          >
            <div class="bubble-author">
              <span>{{ message.role === "user" ? "你" : selectedNode?.groupName || "边侧 QwenPaw" }}</span>
              <time>{{ formatTime(message.createdAt) }}</time>
            </div>
            <div class="chat-bubble">{{ message.content }}</div>
          </article>
        </div>
      </div>

      <div v-if="nodes.length" class="composer">
        <textarea
          v-model="draft"
          placeholder="你想让边侧 QwenPaw 做什么"
          :disabled="streaming || !selectedNodeId"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <button class="send-button" :disabled="streaming || !draft.trim() || !selectedNodeId" @click="sendMessage">
          {{ streaming ? "边侧执行中" : "发送到边侧" }}
        </button>
      </div>

      <div v-if="error" class="flash flash-error">{{ error }}</div>
    </section>

    <div v-if="taskModalOpen" class="modal-backdrop">
      <div class="modal-card task-modal">
        <div class="modal-head">
          <div>
            <p>EDGE CRON</p>
            <h3>{{ selectedNode?.groupName || "边侧 QwenPaw" }} 的任务</h3>
          </div>
          <button class="modal-close" @click="taskModalOpen = false">×</button>
        </div>

        <input v-model="taskName" class="task-name-input" placeholder="请输入任务名称" />

        <label class="task-field">
          <span>执行消息内容</span>
          <textarea v-model="taskText" class="task-textarea" placeholder="请输入"></textarea>
        </label>

        <div class="prompt-chips">
          <button @click="setTaskTemplate('每天汇总一次市场风向标')">📈 市场风向标</button>
          <button @click="setTaskTemplate('全网热榜速递，并总结最值得关注的 3 条')">🔥 全网热榜速递</button>
          <button @click="setTaskTemplate('监控汇率和金价变化，异常波动时给出提醒')">🌍 汇率/金价监控</button>
        </div>

        <section class="task-section">
          <span>执行频率</span>
          <div class="frequency-grid">
            <button :class="{ active: taskFrequency === 'daily' }" @click="taskFrequency = 'daily'">
              每天
            </button>
            <button :class="{ active: taskFrequency === 'weekly' }" @click="taskFrequency = 'weekly'">
              每周
            </button>
            <button :class="{ active: taskFrequency === 'interval' }" @click="taskFrequency = 'interval'">
              每隔一段时间
            </button>
            <button :class="{ active: taskFrequency === 'once' }" @click="taskFrequency = 'once'">
              只执行一次
            </button>
          </div>
        </section>

        <section v-if="taskFrequency !== 'once'" class="task-section">
          <span>执行时间</span>
          <div class="schedule-row">
            <input v-if="taskFrequency !== 'interval'" v-model="taskTime" type="time" />
            <template v-else>
              <input v-model.number="intervalValue" min="1" type="number" />
              <select v-model="intervalUnit">
                <option value="minutes">分钟</option>
                <option value="hours">小时</option>
                <option value="days">天</option>
              </select>
            </template>
          </div>
        </section>

        <div class="modal-actions">
          <button class="btn-secondary" @click="taskModalOpen = false">取消</button>
          <button class="btn-primary" :disabled="streaming || !taskText.trim()" @click="submitTask">
            确认
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
