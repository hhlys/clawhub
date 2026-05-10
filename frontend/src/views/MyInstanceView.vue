<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { apiRequest } from "../api";
import type { Capacity, ManagedInstance, ProductType, ProvisioningProfile } from "../types";

const instance = ref<ManagedInstance | null>(null);
const capacity = ref<Capacity | null>(null);
const provisioning = ref<ProvisioningProfile | null>(null);
const error = ref("");
const message = ref("");
const createDialogOpen = ref(false);

const form = reactive({
  productType: "QWENPAW" as ProductType,
  productVersion: "1.0"
});

const canCreate = computed(() => !instance.value);
const nextPortText = computed(() => provisioning.value?.nextAvailableHostPort ?? "-");

function openCreateDialog() {
  if (!canCreate.value) {
    return;
  }
  createDialogOpen.value = true;
}

function closeCreateDialog() {
  createDialogOpen.value = false;
}

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING: "创建中",
    RUNNING: "在线",
    STOPPED: "已停止",
    FAILED: "异常",
    DELETED: "已删除"
  };
  return map[status] ?? status;
}

async function loadAll() {
  try {
    const [ownedInstance, platformCapacity, profile] = await Promise.all([
      apiRequest<ManagedInstance | undefined>("/api/me/instance"),
      apiRequest<Capacity>("/api/platform/capacity"),
      apiRequest<ProvisioningProfile>("/api/platform/provisioning")
    ]);
    instance.value = ownedInstance ?? null;
    capacity.value = platformCapacity;
    provisioning.value = profile;
    form.productVersion = profile.defaultProductVersion;
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
      bodyJson: form
    });
    message.value = "云端 Claw 创建成功。";
    closeCreateDialog();
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "创建云端 Claw 失败";
  }
}

async function runAction(action: "start" | "stop" | "restart" | "delete") {
  error.value = "";
  message.value = "";
  try {
    if (action === "delete") {
      await apiRequest<void>("/api/me/instance", { method: "DELETE" });
      instance.value = null;
      message.value = "云端 Claw 已删除。";
    } else {
      instance.value = await apiRequest<ManagedInstance>(`/api/me/instance/${action}`, { method: "POST" });
      const actionText = action === "start" ? "启动" : action === "stop" ? "停止" : "重启";
      message.value = `云端 Claw 已${actionText}。`;
    }
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : `操作失败：${action}`;
  }
}

onMounted(loadAll);
</script>

<template>
  <section class="content-page">
    <div class="trial-banner">
      <strong>新用户处于体验版时享有一次试用 7 天云端 Claw 权益。</strong>
      <button>我知道了</button>
    </div>

    <div class="quick-actions">
      <button class="create-tile" :disabled="!canCreate" @click="openCreateDialog">
        <span class="tile-visual cloud-visual"></span>
        <span>
          <strong>创建云端 Claw</strong>
          <small>配置云端智能助手</small>
        </span>
      </button>

      <button class="create-tile" disabled>
        <span class="tile-visual local-visual"></span>
        <span>
          <strong>部署本地 Claw</strong>
          <small>桌面端安装包后续开放</small>
        </span>
      </button>
    </div>

    <div class="section-row">
      <h2>我的 Claw</h2>
      <button class="refresh-button" @click="loadAll">刷新</button>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="message" class="flash flash-success">{{ message }}</div>

    <div v-if="instance" class="claw-card">
      <div class="claw-card-head">
        <div class="claw-avatar"></div>
        <div>
          <h3>{{ instance.instanceName }}</h3>
          <p>
            <span class="cloud-badge">云端</span>
            <span>{{ instance.productType }} {{ instance.productVersion }}</span>
          </p>
          <p class="expire-line">访问端口 {{ instance.hostPort }}，数据目录已持久化</p>
        </div>
        <span :class="['status-pill', `status-${instance.status.toLowerCase()}`]">
          {{ statusText(instance.status) }}
        </span>
      </div>

      <div class="claw-meta">
        <span>{{ instance.host }}</span>
        <span>{{ instance.containerName }}</span>
        <span>{{ instance.dockerImage }}</span>
      </div>

      <div class="claw-actions">
        <router-link class="primary-action" :to="{ path: '/chat', query: { instanceId: String(instance.id) } }">
          对话
        </router-link>
        <button class="circle-action" @click="runAction('start')">启</button>
        <button class="circle-action" @click="runAction('stop')">停</button>
        <button class="circle-action" @click="runAction('restart')">重</button>
        <button class="circle-action danger" @click="runAction('delete')">删</button>
      </div>
    </div>

    <div v-else class="empty-claw">
      <h3>还没有云端 Claw</h3>
      <p>点击上方“创建云端 Claw”，系统会自动分配镜像、IP、端口、实例名和容器名。</p>
      <button class="primary-action" @click="openCreateDialog">创建云端 Claw</button>
    </div>

    <div v-if="createDialogOpen" class="modal-backdrop" @click.self="closeCreateDialog">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <p>Cloud Claw</p>
            <h3>创建云端 Claw</h3>
          </div>
          <button class="ghost-button" @click="closeCreateDialog">关闭</button>
        </div>

        <p class="modal-copy">
          控制台会自动分配实例名称、容器名称、访问主机、可用端口和访问地址。当前仅开放 QwenPaw 云实例。
        </p>

        <div class="provisioning-summary" v-if="provisioning">
          <div>
            <span>默认访问主机</span>
            <strong>{{ provisioning.defaultHost }}</strong>
          </div>
          <div>
            <span>下一可用端口</span>
            <strong>{{ nextPortText }}</strong>
          </div>
          <div>
            <span>容器端口</span>
            <strong>{{ provisioning.containerPort }}</strong>
          </div>
        </div>

        <div class="form-grid compact-form">
          <label>
            <span>产品类型</span>
            <select v-model="form.productType">
              <option value="QWENPAW">QwenPaw</option>
            </select>
          </label>
          <label>
            <span>版本号</span>
            <select v-model="form.productVersion">
              <option v-for="version in provisioning?.supportedProductVersions ?? ['1.0']" :key="version" :value="version">
                {{ version }}
              </option>
            </select>
          </label>
        </div>

        <div class="modal-actions">
          <button class="ghost-button" @click="closeCreateDialog">取消</button>
          <button class="primary-action" @click="createInstance">确认创建</button>
        </div>
      </div>
    </div>
  </section>
</template>
