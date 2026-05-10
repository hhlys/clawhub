<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { apiRequest } from "../api";
import type { InstanceChatMessage, InstanceChatSession, ManagedInstance } from "../types";

type ConsoleMode = "dialog" | "claw";
type ChatRole = "user" | "assistant";

interface ChatMessage {
  id: number;
  role: ChatRole;
  content: string;
  createdAt: string;
}

const route = useRoute();
const instance = ref<ManagedInstance | null>(null);
const sessions = ref<InstanceChatSession[]>([]);
const activeSession = ref<InstanceChatSession | null>(null);
const loading = ref(true);
const sessionsLoading = ref(false);
const error = ref("");
const mode = ref<ConsoleMode>("dialog");
const draft = ref("");
const streaming = ref(false);
const messages = ref<ChatMessage[]>([]);

const requestedInstanceId = computed(() => {
  const value = route.query.instanceId;
  return Array.isArray(value) ? value[0] : value;
});

const frameTitle = computed(() => {
  if (!instance.value) {
    return "Claw 控制台";
  }
  return `${instance.value.instanceName} 控制台`;
});

async function loadInstance() {
  loading.value = true;
  error.value = "";
  try {
    const ownedInstance = await apiRequest<ManagedInstance | undefined>("/api/me/instance");
    instance.value = ownedInstance ?? null;

    if (
      requestedInstanceId.value &&
      ownedInstance &&
      String(ownedInstance.id) !== requestedInstanceId.value
    ) {
      error.value = "当前账号没有找到你选择的云端 Claw，已展示当前账号的默认实例。";
    }

    if (instance.value) {
      await loadSessions(true);
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : "加载云端 Claw 失败";
  } finally {
    loading.value = false;
  }
}

async function loadSessions(selectFirst = false) {
  sessionsLoading.value = true;
  try {
    sessions.value = await apiRequest<InstanceChatSession[]>("/api/me/instance/chat/sessions");
    if (selectFirst && sessions.value.length > 0) {
      await selectSession(sessions.value[0]);
    }
  } finally {
    sessionsLoading.value = false;
  }
}

async function createSession() {
  if (!instance.value || streaming.value) {
    return null;
  }
  const session = await apiRequest<InstanceChatSession>("/api/me/instance/chat/sessions", {
    method: "POST"
  });
  activeSession.value = session;
  messages.value = [];
  sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)];
  return session;
}

async function selectSession(session: InstanceChatSession) {
  if (streaming.value) {
    return;
  }
  activeSession.value = session;
  const storedMessages = await apiRequest<InstanceChatMessage[]>(
    `/api/me/instance/chat/sessions/${session.id}/messages`
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

  if (!state.sawContentDelta && event.object === "message") {
    appendAssistantText(assistantMessageId, textDelta);
  }
}

async function ensureSession() {
  if (activeSession.value) {
    return activeSession.value;
  }
  return createSession();
}

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || streaming.value || !instance.value) {
    return;
  }

  const session = await ensureSession();
  if (!session) {
    return;
  }

  error.value = "";
  messages.value.push({
    id: Date.now(),
    role: "user",
    content: text,
    createdAt: new Date().toISOString()
  });
  draft.value = "";

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
    const response = await fetch("/api/me/instance/chat/stream", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: text,
        sessionId: session.id,
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
        if (event.object === "error") {
          throw new Error(String(event.message || "QwenPaw channel error"));
        }
        captureAssistantText(event, assistantMessageId, captureState);
      }
    }

    const assistant = messages.value.find((item) => item.id === assistantMessageId);
    if (assistant && !assistant.content.trim()) {
      assistant.content = "任务已完成，但没有返回文本内容。";
    }
    await loadSessions(false);
    const refreshed = sessions.value.find((item) => item.id === session.id);
    if (refreshed) {
      activeSession.value = refreshed;
    }
  } catch (err) {
    const assistant = messages.value.find((item) => item.id === assistantMessageId);
    const message = err instanceof Error ? err.message : "发送失败";
    if (assistant) {
      assistant.content = `调用容器内 QwenPaw 失败：${message}`;
    }
    error.value = message;
  } finally {
    streaming.value = false;
  }
}

function formatSessionTime(value: string) {
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

onMounted(loadInstance);
</script>

<template>
  <section class="chat-layout">
    <aside class="conversation-panel">
      <div class="bot-profile">
        <div class="bot-avatar">C</div>
        <div>
          <strong>{{ instance?.instanceName ?? "my claw" }}</strong>
          <span>{{ instance?.status === "RUNNING" ? "在线" : "未连接" }}</span>
        </div>
      </div>

      <button class="new-chat-button" :disabled="!instance || streaming" @click="createSession">
        新对话
      </button>

      <div class="side-menu">
        <router-link to="/my-claw">定时任务</router-link>
        <router-link to="/files">文件空间</router-link>
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
          <strong>{{ session.title || "新对话" }}</strong>
          <small>{{ formatSessionTime(session.lastMessageAt) }}</small>
        </button>
        <span v-if="!sessions.length && !sessionsLoading" class="recent-empty">
          暂无历史对话
        </span>
      </div>
    </aside>

    <section class="chat-main embedded">
      <div class="chat-topline">
        <strong>{{ instance?.instanceName ?? "my claw" }}</strong>
        <div class="view-switcher">
          <button
            :class="['view-option', { active: mode === 'dialog' }]"
            @click="mode = 'dialog'"
          >
            对话视图
          </button>
          <button
            :class="['view-option', { active: mode === 'claw' }]"
            @click="mode = 'claw'"
          >
            龙虾视图
          </button>
        </div>
      </div>

      <div v-if="loading" class="empty-chat">
        <h2>正在打开控制台...</h2>
        <p>ClawHub 正在读取你的云端 Claw 实例信息。</p>
      </div>

      <div v-else-if="!instance" class="empty-chat">
        <h2>还没有可打开的云端 Claw</h2>
        <p>先创建一个云端 Claw，再回到这里进入控制台聊天窗口。</p>
        <router-link class="primary-action" to="/my-claw">去创建云端 Claw</router-link>
      </div>

      <div v-else-if="mode === 'claw'" class="console-frame-wrap">
        <iframe class="instance-frame" :src="instance.entryUrl" :title="frameTitle"></iframe>
      </div>

      <div v-else class="dialog-stage">
        <div class="dialog-feed">
          <div v-if="!messages.length" class="empty-chat compact">
            <h2>{{ activeSession ? "这是一段新对话" : "开始一段新对话" }}</h2>
            <p>这里会通过 clawhub_chat channel 展示容器内 QwenPaw 的流式回复。</p>
          </div>

          <article
            v-for="message in messages"
            :key="message.id"
            :class="['chat-bubble-row', message.role]"
          >
            <div class="bubble-author">
              <span>{{ message.role === "user" ? "你" : instance.instanceName }}</span>
              <time>{{ formatSessionTime(message.createdAt) }}</time>
            </div>
            <div class="chat-bubble">{{ message.content }}</div>
          </article>
        </div>
      </div>

      <div v-if="mode === 'dialog' && instance" class="composer">
        <textarea
          v-model="draft"
          placeholder="你想让我做什么"
          :disabled="streaming"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <button class="send-button" :disabled="streaming || !draft.trim()" @click="sendMessage">
          {{ streaming ? "执行中" : "发送" }}
        </button>
      </div>

      <div v-if="error" class="flash flash-error">{{ error }}</div>
    </section>
  </section>
</template>
