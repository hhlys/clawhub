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
    <div class="auth-hero">
      <div class="auth-story">
        <p class="eyebrow">我的AI云平台</p>
        <h1>登录进入我的Claw</h1>
        <p class="auth-copy">
          登录后进入你的专属 Claw 空间，统一创建、查看和管理自己的 AI 实例。
        </p>
        <div class="auth-points">
          <div class="auth-point">
            <strong>1用户1实例</strong>
            <span>每位用户同一时间只持有一个 Claw 实例。</span>
          </div>
          <div class="auth-point">
            <strong>统一入口管理</strong>
            <span>创建、启停、重启和进入实例都在同一个平台完成。</span>
          </div>
          <div class="auth-point">
            <strong>资源总量受控</strong>
            <span>平台实例数受总容量限制，先确保资源稳定可控。</span>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <p class="eyebrow">登录</p>
        <h2>进入我的AI云平台</h2>
        <p class="muted">继续访问你的我的Claw实例空间。</p>
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
