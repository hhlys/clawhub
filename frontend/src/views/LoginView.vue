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
    <header class="login-header">
      <router-link class="login-brand" to="/login">
        <span class="brand-mark"></span>
        <span>我的龙虾</span>
      </router-link>
    </header>

    <div class="login-center">
      <div class="login-title">
        <h1>欢迎登录我的龙虾</h1>
        <div class="login-tabs">
          <span>账号登录</span>
        </div>
      </div>

      <div class="auth-card login-card">
        <div v-if="error" class="flash flash-error">{{ error }}</div>
        <form @submit.prevent="submit">
          <div class="login-field">
            <input
              v-model="username"
              name="lobster_account"
              autocomplete="off"
              placeholder="请输入账号"
            />
          </div>
          <div class="login-field">
            <input
              v-model="password"
              name="lobster_passcode"
              type="password"
              autocomplete="new-password"
              placeholder="请输入密码"
            />
          </div>
          <label class="login-agreement">
            <input type="checkbox" checked />
            <span>我已阅读并同意用户协议和隐私政策</span>
          </label>
          <button class="login-submit" type="submit">登录</button>
          <div class="login-register">
            <span>如果没有账号，</span>
            <router-link to="/register">立即注册</router-link>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
