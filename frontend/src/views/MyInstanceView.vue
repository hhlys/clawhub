<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { apiRequest } from "../api";
import type { ManagedInstance } from "../types";

const instance = ref<ManagedInstance | null>(null);
const error = ref("");
const message = ref("");
const createDialogOpen = ref(false);
const actionMenuOpen = ref(false);

const form = reactive({
  instanceName: ""
});

const canCreate = computed(() => !instance.value);
const canSubmitCreate = computed(() => form.instanceName.trim().length > 0);

function openCreateDialog() {
  if (!canCreate.value) {
    return;
  }
  createDialogOpen.value = true;
}

function closeCreateDialog() {
  createDialogOpen.value = false;
}

async function loadAll() {
  try {
    const ownedInstance = await apiRequest<ManagedInstance | undefined>("/api/me/instance");
    instance.value = ownedInstance ?? null;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "加载云实例失败";
  }
}

async function createInstance() {
  error.value = "";
  message.value = "";
  try {
    instance.value = await apiRequest<ManagedInstance>("/api/me/instance", {
      method: "POST",
      bodyJson: {
        instanceName: form.instanceName.trim()
      }
    });
    message.value = "云端龙虾创建成功。";
    closeCreateDialog();
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "创建云端龙虾失败";
  }
}

async function runAction(action: "start" | "stop" | "restart" | "delete") {
  error.value = "";
  message.value = "";
  actionMenuOpen.value = false;
  try {
    if (action === "delete") {
      await apiRequest<void>("/api/me/instance", { method: "DELETE" });
      instance.value = null;
      message.value = "云端龙虾已删除。";
    } else {
      instance.value = await apiRequest<ManagedInstance>(`/api/me/instance/${action}`, { method: "POST" });
      const actionText = action === "start" ? "启动" : action === "stop" ? "停止" : "重启";
      message.value = `云端龙虾已${actionText}。`;
    }
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : `操作失败：${action}`;
  }
}

function toggleActionMenu() {
  actionMenuOpen.value = !actionMenuOpen.value;
}

onMounted(loadAll);
</script>

<template>
  <section class="content-page">
    <div class="trial-banner">
      <strong>新用户处于体验版时享有一次试用 7 天云端龙虾权益。</strong>
      <button>我知道了</button>
    </div>

    <div class="quick-actions">
      <button class="create-tile" :disabled="!canCreate" @click="openCreateDialog">
        <span class="tile-visual cloud-visual"></span>
        <span>
          <strong>创建云端龙虾</strong>
          <small>配置云端智能助手</small>
        </span>
      </button>

      <button class="create-tile" disabled>
        <span class="tile-visual local-visual"></span>
        <span>
          <strong>部署本地龙虾</strong>
          <small>桌面端安装包后续开放</small>
        </span>
      </button>
    </div>

    <div class="section-row">
      <h2>我的龙虾</h2>
      <button class="refresh-button" @click="loadAll">刷新</button>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="message" class="flash flash-success">{{ message }}</div>

    <div v-if="instance" class="claw-card">
      <div class="claw-card-head">
        <div class="claw-avatar"></div>
        <div>
          <h3>{{ instance.instanceName }}</h3>
          <span class="cloud-badge">云端</span>
        </div>
      </div>

      <div class="claw-actions compact">
        <router-link class="primary-action" :to="{ path: '/chat', query: { instanceId: String(instance.id) } }">
          对话
        </router-link>
        <div class="claw-more">
          <button class="circle-action" title="更多操作" @click="toggleActionMenu">⋮</button>
          <div v-if="actionMenuOpen" class="claw-action-menu">
            <button @click="runAction('restart')"><span>↩</span>重启</button>
            <button class="danger" @click="runAction('delete')"><span>⌫</span>删除</button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-claw">
      <h3>还没有云端龙虾</h3>
      <button class="primary-action" @click="openCreateDialog">创建云端龙虾</button>
    </div>

    <div v-if="createDialogOpen" class="modal-backdrop" @click.self="closeCreateDialog">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <p>云端龙虾</p>
            <h3>创建云端龙虾</h3>
          </div>
          <button class="ghost-button" @click="closeCreateDialog">关闭</button>
        </div>

        <p class="modal-copy">
          给你的云端龙虾起一个好记的名字，后续对话、卡片和实例管理都会使用这个名称。
        </p>

        <div class="form-grid compact-form">
          <label>
            <span>龙虾名称</span>
            <input v-model.trim="form.instanceName" maxlength="64" placeholder="例如：我的工作龙虾" />
          </label>
        </div>

        <div class="modal-actions">
          <button class="ghost-button" @click="closeCreateDialog">取消</button>
          <button class="primary-action" :disabled="!canSubmitCreate" @click="createInstance">确认创建</button>
        </div>
      </div>
    </div>
  </section>
</template>
