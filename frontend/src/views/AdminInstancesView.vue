<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { apiRequest } from "../api";
import type { Capacity, ManagedInstance, ProductType, User } from "../types";

const instances = ref<ManagedInstance[]>([]);
const users = ref<User[]>([]);
const capacity = ref<Capacity | null>(null);
const error = ref("");
const message = ref("");

const form = reactive({
  ownerUserId: 0,
  productType: "QWENPAW" as ProductType,
  productVersion: "1.0",
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

const remainingText = computed(() => {
  if (!capacity.value) {
    return "Loading capacity...";
  }
  return `${capacity.value.currentInstanceCount} / ${capacity.value.instanceLimit} in use`;
});

function syncDerivedFields() {
  if (form.instanceName) {
    form.dataVolumeHostPath = `/srv/clawhub/instances/${form.instanceName}`;
    form.containerName = `qwenpaw-${form.instanceName}`;
  }
}

async function loadAll() {
  const [instanceData, userData, capacityData] = await Promise.all([
    apiRequest<ManagedInstance[]>("/api/admin/instances"),
    apiRequest<User[]>("/api/admin/users"),
    apiRequest<Capacity>("/api/platform/capacity")
  ]);
  instances.value = instanceData;
  users.value = userData.filter((user) => user.status === "ACTIVE");
  capacity.value = capacityData;
  if (!form.ownerUserId && users.value.length > 0) {
    form.ownerUserId = users.value[0].id;
  }
}

async function createInstance() {
  error.value = "";
  message.value = "";
  try {
    await apiRequest<ManagedInstance>("/api/admin/instances", {
      method: "POST",
      bodyJson: form
    });
    message.value = "Managed instance created.";
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to create instance";
  }
}

async function action(id: number, actionName: "start" | "stop" | "restart" | "delete") {
  error.value = "";
  message.value = "";
  try {
    if (actionName === "delete") {
      await apiRequest<void>(`/api/admin/instances/${id}`, { method: "DELETE" });
    } else {
      await apiRequest<ManagedInstance>(`/api/admin/instances/${id}/${actionName}`, { method: "POST" });
    }
    message.value = `Instance ${actionName} completed.`;
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : `Failed to ${actionName} instance`;
  }
}

onMounted(async () => {
  try {
    await loadAll();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load admin data";
  }
});
</script>

<template>
  <div class="panel">
    <div class="section-head">
      <div>
        <p class="eyebrow">Admin Instances</p>
        <h2>Global instance operations</h2>
      </div>
      <span class="muted">{{ remainingText }}</span>
    </div>

    <div class="metric-grid" v-if="capacity">
      <div class="metric">
        <span>Limit</span>
        <strong>{{ capacity.instanceLimit }}</strong>
      </div>
      <div class="metric">
        <span>Current</span>
        <strong>{{ capacity.currentInstanceCount }}</strong>
      </div>
      <div class="metric">
        <span>Remaining</span>
        <strong>{{ capacity.remainingInstanceSlots }}</strong>
      </div>
    </div>

    <div v-if="error" class="flash flash-error">{{ error }}</div>
    <div v-if="message" class="flash flash-success">{{ message }}</div>

    <div class="grid-2">
      <div class="card">
        <p class="eyebrow">Create Managed Instance</p>
        <h3>Admin-assigned instance</h3>
        <div class="form-grid">
          <div class="field">
            <label>Owner</label>
            <select v-model.number="form.ownerUserId">
              <option v-for="user in users" :key="user.id" :value="user.id">{{ user.username }}</option>
            </select>
          </div>
          <div class="field">
            <label>Product Type</label>
            <select v-model="form.productType">
              <option value="QWENPAW">QwenPaw</option>
              <option value="OPENCLAW">Open龙虾</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div class="field">
            <label>Version</label>
            <input v-model="form.productVersion" />
          </div>
          <div class="field">
            <label>Instance Name</label>
            <input v-model="form.instanceName" @input="syncDerivedFields" />
          </div>
          <div class="field">
            <label>Container Name</label>
            <input v-model="form.containerName" />
          </div>
          <div class="field">
            <label>Docker Image</label>
            <input v-model="form.dockerImage" />
          </div>
          <div class="field">
            <label>Host</label>
            <input v-model="form.host" />
          </div>
          <div class="field">
            <label>Host Port</label>
            <input v-model.number="form.hostPort" type="number" />
          </div>
          <div class="field">
            <label>Container Port</label>
            <input v-model.number="form.containerPort" type="number" />
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
            <input v-model="form.publicBaseUrl" />
          </div>
        </div>
        <div class="btn-row" style="margin-top: 16px;">
          <button class="btn btn-primary" @click="createInstance">Create Instance</button>
        </div>
      </div>

      <div class="card">
        <p class="eyebrow">Managed Inventory</p>
        <h3>All platform instances</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Owner</th>
                <th>Status</th>
                <th>Entry</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="instance in instances" :key="instance.id">
                <td>
                  <strong>{{ instance.instanceName }}</strong>
                  <div class="mono">{{ instance.containerName }}</div>
                </td>
                <td>{{ instance.ownerUsername }}</td>
                <td><span :class="['status-pill', `status-${instance.status.toLowerCase()}`]">{{ instance.status }}</span></td>
                <td><a :href="instance.entryUrl" target="_blank" rel="noreferrer">{{ instance.entryUrl }}</a></td>
                <td>
                  <div class="btn-row">
                    <button class="btn btn-soft" @click="action(instance.id, 'start')">Start</button>
                    <button class="btn btn-warn" @click="action(instance.id, 'stop')">Stop</button>
                    <button class="btn btn-soft" @click="action(instance.id, 'restart')">Restart</button>
                    <button class="btn btn-danger" @click="action(instance.id, 'delete')">Delete</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
