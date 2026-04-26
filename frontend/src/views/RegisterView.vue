<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const username = ref("");
const password = ref("");
const message = ref("");
const error = ref("");

async function submit() {
  error.value = "";
  message.value = "";
  try {
    await auth.register(username.value, password.value);
    message.value = "Registration succeeded. Sign in with the new account.";
    setTimeout(() => router.push("/login"), 800);
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Registration failed";
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <p class="eyebrow">Register</p>
      <h2>Create a user account</h2>
      <p class="muted">New accounts are created as regular users and can own one instance at a time.</p>
      <div v-if="error" class="flash flash-error">{{ error }}</div>
      <div v-if="message" class="flash flash-success">{{ message }}</div>
      <form @submit.prevent="submit">
        <div class="field">
          <label>Username</label>
          <input v-model="username" autocomplete="username" />
        </div>
        <div class="field">
          <label>Password</label>
          <input v-model="password" type="password" autocomplete="new-password" />
        </div>
        <div class="auth-actions">
          <button class="btn btn-primary" type="submit">Register</button>
          <router-link class="muted" to="/login">Back to login</router-link>
        </div>
      </form>
    </div>
  </div>
</template>
