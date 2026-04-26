<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const username = ref("");
const password = ref("");
const error = ref("");

async function submit() {
  error.value = "";
  try {
    await auth.login(username.value, password.value);
    router.push(auth.isAdmin ? "/admin/instances" : "/my-instance");
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Login failed";
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <p class="eyebrow">Sign In</p>
      <h2>Enter ClawHub</h2>
      <p class="muted">Sign in to manage your assigned instance or the full platform.</p>
      <div v-if="error" class="flash flash-error">{{ error }}</div>
      <form @submit.prevent="submit">
        <div class="field">
          <label>Username</label>
          <input v-model="username" autocomplete="username" />
        </div>
        <div class="field">
          <label>Password</label>
          <input v-model="password" type="password" autocomplete="current-password" />
        </div>
        <div class="auth-actions">
          <button class="btn btn-primary" type="submit">Login</button>
          <router-link class="muted" to="/register">Create account</router-link>
        </div>
      </form>
    </div>
  </div>
</template>
