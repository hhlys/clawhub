<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { apiRequest } from "../api";
import type { Capacity, ManagedInstance, ProductType } from "../types";

const instance = ref<ManagedInstance | null>(null);
const capacity = ref<Capacity | null>(null);
const error = ref("");
const message = ref("");

const form = reactive({
  productType: "QWENPAW" as ProductType,
  instanceName: "",
  containerName: "",
  dockerImage: "qwenpaw:local",
  host: "",
  hostPort: 18088,
  containerPort: 8088,
  dataVolumeHostPath: "/srv/clawhub/instances/demo-qwenpaw-01",
  dataVolumeContainerPath: "/root/.qwenpaw",
  publicBaseUrl: "",
  autoStart: true
});

function syncDerivedFields() {
  if (form.instanceName) {
    form.dataVolumeHostPath = `/srv/clawhub/instances/${form.instanceName}`;
    form.containerName = `qwenpaw-${form.instanceName}`;
  }
}

async function loadAll() {
  try {
    instance.value = (await apiRequest<ManagedInstance | undefined>("/api/me/instance")) ?? null;
    capacity.value = await apiRequest<Capacity>("/api/platform/capacity");
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
    message.value = "Instance created successfully.";
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
      message.value = "Instance deleted.";
    } else {
      instance.value = await apiRequest<ManagedInstance>(`/api/me/instance/${action}`, { method: "POST" });
      message.value = `Instance ${action}ed.`;
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
        <p class="eyebrow">My Instance</p>
        <h2>User self-service instance lifecycle</h2>
      </div>
      <span v-if="capacity" class="muted">
        Capacity: {{ capacity.currentInstanceCount }} / {{ capacity.instanceLimit }}
      </span>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="message" class="flash flash-success">{{ message }}</div>

    <div v-if="instance" class="grid-2">
      <div class="card">
        <p class="eyebrow">Assigned Instance</p>
        <h3>{{ instance.instanceName }}</h3>
        <p class="muted">One user can only hold one instance at a time.</p>
        <p><span :class="['status-pill', `status-${instance.status.toLowerCase()}`]">{{ instance.status }}</span></p>
        <p class="mono">{{ instance.containerName }}</p>
        <p class="mono">{{ instance.entryUrl }}</p>
        <div class="btn-row">
          <a class="btn btn-secondary" :href="instance.entryUrl" target="_blank" rel="noreferrer">Open</a>
          <button class="btn btn-soft" @click="runAction('start')">Start</button>
          <button class="btn btn-warn" @click="runAction('stop')">Stop</button>
          <button class="btn btn-soft" @click="runAction('restart')">Restart</button>
          <button class="btn btn-danger" @click="runAction('delete')">Delete</button>
        </div>
      </div>
      <div class="card">
        <p class="eyebrow">Runtime</p>
        <p><strong>Image:</strong> {{ instance.dockerImage }}</p>
        <p><strong>Port:</strong> {{ instance.hostPort }} -> {{ instance.containerPort }}</p>
        <p><strong>Volume:</strong></p>
        <p class="mono">{{ instance.dataVolumeHostPath }}</p>
        <p class="mono">{{ instance.dataVolumeContainerPath }}</p>
      </div>
    </div>

    <div v-else class="card">
      <p class="eyebrow">Create</p>
      <h3>No instance assigned yet</h3>
      <p class="muted">Create your single allowed instance. If the platform already has five instances total, creation will be rejected.</p>
      <div class="form-grid">
        <div class="field">
          <label>Product Type</label>
          <select v-model="form.productType">
            <option value="QWENPAW">QwenPaw</option>
            <option value="OPENCLAW">OpenClaw</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div class="field">
          <label>Instance Name</label>
          <input v-model="form.instanceName" @input="syncDerivedFields" placeholder="demo-qwenpaw-01" />
        </div>
        <div class="field">
          <label>Container Name</label>
          <input v-model="form.containerName" placeholder="qwenpaw-demo-qwenpaw-01" />
        </div>
        <div class="field">
          <label>Docker Image</label>
          <input v-model="form.dockerImage" />
        </div>
        <div class="field">
          <label>Host</label>
          <input v-model="form.host" placeholder="Public server IP" />
        </div>
        <div class="field">
          <label>Host Port</label>
          <input v-model.number="form.hostPort" type="number" />
        </div>
        <div class="field">
          <label>Container Port</label>
          <input v-model.number="form.containerPort" type="number" />
        </div>
        <div class="field">
          <label>Auto Start</label>
          <select v-model="form.autoStart">
            <option :value="true">true</option>
            <option :value="false">false</option>
          </select>
        </div>
        <div class="field-full">
          <label>Host Volume Path</label>
          <input v-model="form.dataVolumeHostPath" />
        </div>
        <div class="field-full">
          <label>Container Volume Path</label>
          <input v-model="form.dataVolumeContainerPath" />
        </div>
        <div class="field-full">
          <label>Public Base URL</label>
          <input v-model="form.publicBaseUrl" placeholder="http://your-server-ip:18088" />
        </div>
      </div>
      <div class="btn-row" style="margin-top: 16px;">
        <button class="btn btn-primary" @click="createInstance">Create My Instance</button>
      </div>
    </div>
  </div>
</template>
