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
    router.push("/my-claw");
  } catch (err) {
    error.value = err instanceof Error ? err.message : "登录失败";
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-hero">
      <div class="auth-story">
        <p class="eyebrow">ClawHub</p>
        <h1>登录进入我的 Claw</h1>
        <p class="auth-copy">
          统一创建、查看和管理自己的云端 Claw 实例。普通用户登录后即可进入“我的 Claw”创建云实例。
        </p>
        <div class="auth-points">
          <div class="auth-point">
            <strong>用户隔离</strong>
            <span>每个用户只看到自己的云端实例和后续任务。</span>
          </div>
          <div class="auth-point">
            <strong>统一入口</strong>
            <span>创建、启动、停止、重启和进入实例都在同一个控制台完成。</span>
          </div>
          <div class="auth-point">
            <strong>云地通信扩展</strong>
            <span>后续可在同一入口纳管边侧节点、技能下发和任务回传。</span>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <p class="eyebrow">Login</p>
        <h2>欢迎回来</h2>
        <p class="muted">默认普通用户：hhl / Hl123321*</p>
        <div v-if="error" class="flash flash-error">{{ error }}</div>
        <form @submit.prevent="submit">
          <div class="field">
            <label>用户名</label>
            <input v-model="username" autocomplete="username" />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="password" type="password" autocomplete="current-password" />
          </div>
          <div class="auth-actions">
            <button class="btn btn-primary" type="submit">登录</button>
            <router-link class="muted" to="/register">立即注册</router-link>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
