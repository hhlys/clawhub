<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { apiRequest } from "../api";
import type { User, UserRole, UserStatus } from "../types";

const users = ref<User[]>([]);
const error = ref("");
const message = ref("");
const form = reactive({
  username: "",
  password: "",
  role: "USER" as UserRole
});

async function loadUsers() {
  users.value = await apiRequest<User[]>("/api/admin/users");
}

async function createUser() {
  error.value = "";
  message.value = "";
  try {
    await apiRequest<User>("/api/admin/users", {
      method: "POST",
      bodyJson: form
    });
    form.username = "";
    form.password = "";
    form.role = "USER";
    message.value = "User created.";
    await loadUsers();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to create user";
  }
}

async function updateRole(user: User, role: UserRole) {
  await apiRequest<User>(`/api/admin/users/${user.id}/role`, {
    method: "PATCH",
    bodyJson: { role }
  });
  await loadUsers();
}

async function updateStatus(user: User, status: UserStatus) {
  await apiRequest<User>(`/api/admin/users/${user.id}/status`, {
    method: "PATCH",
    bodyJson: { status }
  });
  await loadUsers();
}

onMounted(async () => {
  try {
    await loadUsers();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load users";
  }
});
</script>

<template>
  <div class="grid-2">
    <div class="panel">
      <div class="section-head">
        <div>
          <p class="eyebrow">Users</p>
          <h2>Manage platform accounts</h2>
        </div>
      </div>
      <div v-if="error" class="flash flash-error">{{ error }}</div>
      <div v-if="message" class="flash flash-success">{{ message }}</div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Role</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.username }}</td>
              <td>{{ user.role }}</td>
              <td>{{ user.status }}</td>
              <td>
                <div class="btn-row">
                  <button class="btn btn-soft" @click="updateRole(user, user.role === 'ADMIN' ? 'USER' : 'ADMIN')">
                    Toggle Role
                  </button>
                  <button class="btn btn-warn" @click="updateStatus(user, user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')">
                    Toggle Status
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="panel">
      <div class="section-head">
        <div>
          <p class="eyebrow">Create User</p>
          <h2>Provision new accounts</h2>
        </div>
      </div>
      <div class="form-grid">
        <div class="field-full">
          <label>Username</label>
          <input v-model="form.username" />
        </div>
        <div class="field-full">
          <label>Password</label>
          <input v-model="form.password" type="password" />
        </div>
        <div class="field-full">
          <label>Role</label>
          <select v-model="form.role">
            <option value="USER">USER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
      </div>
      <div class="btn-row" style="margin-top: 16px;">
        <button class="btn btn-primary" @click="createUser">Create User</button>
      </div>
    </div>
  </div>
</template>
