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
    error.value = err instanceof Error ? err.message : "Failed to load instance";
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
    message.value = "Claw 实例创建成功。";
    closeCreateDialog();
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to create instance";
  }
}

async function runAction(action: "start" | "stop" | "restart" | "delete") {
  error.value = "";
  message.value = "";
  try {
    if (action === "delete") {
      await apiRequest<void>("/api/me/instance", { method: "DELETE" });
      instance.value = null;
      message.value = "Claw 实例已删除。";
    } else {
      instance.value = await apiRequest<ManagedInstance>(`/api/me/instance/${action}`, { method: "POST" });
      message.value = `Claw 实例已${action === "start" ? "启动" : action === "stop" ? "停止" : "重启"}。`;
    }
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : `Failed to ${action} instance`;
  }
}

onMounted(loadAll);
</script>

<template>
  <div class="panel">
    <div class="section-head">
      <div>
        <p class="eyebrow">我的Claw</p>
        <h2>管理我的 Claw 实例</h2>
      </div>
      <div class="title-actions">
        <span v-if="capacity" class="muted">
          平台容量：{{ capacity.currentInstanceCount }} / {{ capacity.instanceLimit }}
        </span>
        <button class="btn btn-primary" :disabled="!canCreate" @click="openCreateDialog">
          创建Claw
        </button>
      </div>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="message" class="flash flash-success">{{ message }}</div>

    <div v-if="instance" class="grid-2">
      <div class="card">
        <p class="eyebrow">我的实例</p>
        <h3>{{ instance.instanceName }}</h3>
        <p class="muted">你当前持有的 Claw 实例。若需重建，请先删除当前实例。</p>
        <p><span :class="['status-pill', `status-${instance.status.toLowerCase()}`]">{{ instance.status }}</span></p>
        <p class="mono">{{ instance.containerName }}</p>
        <p class="mono">{{ instance.entryUrl }}</p>
        <div class="btn-row">
          <a class="btn btn-secondary" :href="instance.entryUrl" target="_blank" rel="noreferrer">进入实例</a>
          <button class="btn btn-soft" @click="runAction('start')">启动</button>
          <button class="btn btn-warn" @click="runAction('stop')">停止</button>
          <button class="btn btn-soft" @click="runAction('restart')">重启</button>
          <button class="btn btn-danger" @click="runAction('delete')">删除</button>
        </div>
      </div>
      <div class="card">
        <p class="eyebrow">运行信息</p>
        <p><strong>产品：</strong>{{ instance.productType }}</p>
        <p><strong>版本：</strong>{{ instance.productVersion }}</p>
        <p><strong>镜像：</strong>{{ instance.dockerImage }}</p>
        <p><strong>访问地址：</strong>{{ instance.entryUrl }}</p>
        <p><strong>端口：</strong>{{ instance.hostPort }} -> {{ instance.containerPort }}</p>
      </div>
    </div>

    <div v-else class="card empty-card">
      <p class="eyebrow">我的实例</p>
      <h3>你还没有 Claw 实例</h3>
      <p class="muted">点击右上角的“创建Claw”按钮，系统会自动分配镜像、IP、端口、实例名和容器名。</p>
      <div class="btn-row" style="justify-content: center; margin-top: 16px;">
        <button class="btn btn-primary" @click="openCreateDialog">创建Claw</button>
      </div>
    </div>

    <div v-if="createDialogOpen" class="modal-backdrop" @click.self="closeCreateDialog">
      <div class="modal-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">创建 Claw 实例</p>
            <h3>确认产品与版本</h3>
          </div>
          <button class="btn btn-soft" @click="closeCreateDialog">关闭</button>
        </div>
        <p class="muted modal-copy">
          实例创建时，控制面会自动分配实例名称、容器名称、宿主机 IP、可用端口和访问地址。当前仅开放 QwenPaw 1.0。
        </p>
        <div class="provisioning-summary" v-if="provisioning">
          <div class="summary-item">
            <span>默认访问主机</span>
            <strong>{{ provisioning.defaultHost }}</strong>
          </div>
          <div class="summary-item">
            <span>下一可用端口</span>
            <strong>{{ nextPortText }}</strong>
          </div>
          <div class="summary-item">
            <span>容器端口</span>
            <strong>{{ provisioning.containerPort }}</strong>
          </div>
        </div>
        <div class="form-grid compact-form">
          <div class="field">
            <label>产品类型</label>
            <select v-model="form.productType">
              <option value="QWENPAW">QwenPaw</option>
            </select>
          </div>
          <div class="field">
            <label>版本号</label>
            <select v-model="form.productVersion">
              <option v-for="version in provisioning?.supportedProductVersions ?? ['1.0']" :key="version" :value="version">
                {{ version }}
              </option>
            </select>
          </div>
        </div>
        <div class="btn-row modal-actions">
          <button class="btn btn-soft" @click="closeCreateDialog">取消</button>
          <button class="btn btn-primary" @click="createInstance">确认创建</button>
        </div>
      </div>
    </div>
  </div>
</template>
