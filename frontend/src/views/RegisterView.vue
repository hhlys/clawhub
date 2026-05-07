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
    message.value = "注册成功，请使用新账号登录。";
    setTimeout(() => router.push("/login"), 800);
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Registration failed";
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-hero">
      <div class="auth-story">
        <p class="eyebrow">我的AI云平台</p>
        <h1>注册你的Claw账号</h1>
        <p class="auth-copy">
          注册成功后你会获得普通用户身份，可以创建并管理自己的 Claw 实例。
        </p>
        <div class="auth-points">
          <div class="auth-point">
            <strong>默认普通用户</strong>
            <span>公开注册只创建普通用户账号，不会授予管理员权限。</span>
          </div>
          <div class="auth-point">
            <strong>实例唯一约束</strong>
            <span>每位用户最多保有一个实例，需要重建时先删除旧实例。</span>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <p class="eyebrow">注册</p>
        <h2>创建我的AI云平台账号</h2>
        <p class="muted">注册完成后即可登录进入我的Claw。</p>
        <div v-if="error" class="flash flash-error">{{ error }}</div>
        <div v-if="message" class="flash flash-success">{{ message }}</div>
        <form @submit.prevent="submit">
          <div class="field">
            <label>用户名</label>
            <input v-model="username" autocomplete="username" />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="password" type="password" autocomplete="new-password" />
          </div>
          <div class="auth-actions">
            <button class="btn btn-primary" type="submit">注册账号</button>
            <router-link class="muted" to="/login">返回登录</router-link>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
